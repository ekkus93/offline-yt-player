use super::*;

pub(super) fn partial_path(final_path: &Path) -> PathBuf {
    let name = final_path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("asset");
    final_path.with_file_name(format!(".{name}.partial"))
}

pub(super) fn ensure_safe_library_write_path(
    root: &Path,
    final_path: &Path,
    partial_path: &Path,
) -> Result<(), CoreError> {
    let canonical_root = canonical_library_root(root)?;
    let Some(parent) = final_path.parent() else {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path has no parent",
            false,
        ));
    };
    reject_existing_symlink_components(root, parent)?;
    fs::create_dir_all(parent).map_err(io_error)?;
    let canonical_parent = fs::canonicalize(parent).map_err(io_error)?;
    if !canonical_parent.starts_with(&canonical_root) {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the selected library root",
            false,
        ));
    }
    ensure_path_not_symlink(final_path)?;
    ensure_path_not_symlink(partial_path)?;
    Ok(())
}

pub(super) fn reject_symlink_or_non_directory_root(root: &Path) -> Result<(), CoreError> {
    match fs::symlink_metadata(root) {
        Ok(metadata) if metadata.file_type().is_symlink() || !metadata.is_dir() => {
            Err(CoreError::new(
                ErrorKind::InvalidInput,
                "library root must be a real directory, not a symlink",
                false,
            ))
        }
        Ok(_) => Ok(()),
        Err(error) if error.kind() == IoErrorKind::NotFound => Ok(()),
        Err(error) => Err(io_error(error)),
    }
}

pub(super) fn canonical_library_root(root: &Path) -> Result<PathBuf, CoreError> {
    reject_symlink_or_non_directory_root(root)?;
    fs::create_dir_all(root).map_err(io_error)?;
    reject_symlink_or_non_directory_root(root)?;
    fs::canonicalize(root).map_err(io_error)
}

pub(super) fn reject_existing_symlink_components(
    root: &Path,
    target: &Path,
) -> Result<(), CoreError> {
    let relative = target.strip_prefix(root).map_err(|_| {
        CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the selected library root",
            false,
        )
    })?;
    let mut current = root.to_path_buf();
    for component in relative.components() {
        current.push(component.as_os_str());
        match fs::symlink_metadata(&current) {
            Ok(metadata) if metadata.file_type().is_symlink() => {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "library asset path must not contain symlink components",
                    false,
                ));
            }
            Ok(_) => {}
            Err(error) if error.kind() == IoErrorKind::NotFound => break,
            Err(error) => return Err(io_error(error)),
        }
    }
    Ok(())
}

pub(super) fn ensure_path_not_symlink(path: &Path) -> Result<(), CoreError> {
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.file_type().is_symlink() => Err(CoreError::new(
            ErrorKind::InvalidInput,
            "library asset path must not be a symlink",
            false,
        )),
        Ok(_) => Ok(()),
        Err(error) if error.kind() == IoErrorKind::NotFound => Ok(()),
        Err(error) => Err(io_error(error)),
    }
}

pub(super) fn visit_partials(
    canonical_root: &Path,
    directory: &Path,
    visitor: &mut dyn FnMut(&Path) -> Result<(), CoreError>,
) -> Result<(), CoreError> {
    if !directory.exists() {
        return Ok(());
    }
    // Keep one streaming directory iterator per active depth. A pending-path
    // stack can retain every sibling of a very wide imported Library tree.
    let mut directories = vec![fs::read_dir(directory).map_err(io_error)?];
    while let Some(entries) = directories.last_mut() {
        let Some(entry) = entries.next() else {
            directories.pop();
            continue;
        };
        let path = entry.map_err(io_error)?.path();
        // Deleting a partial also unlinks its sidecar, which may still be
        // returned by an existing directory iterator. Ignore only ENOENT.
        let metadata = match fs::symlink_metadata(&path) {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == IoErrorKind::NotFound => continue,
            Err(error) => return Err(io_error(error)),
        };
        if metadata.file_type().is_symlink() {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "library cleanup refused to follow symlink",
                false,
            ));
        }
        if metadata.is_dir() {
            let canonical_directory = fs::canonicalize(&path).map_err(io_error)?;
            if !canonical_directory.starts_with(canonical_root) {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "library cleanup path escapes the selected root",
                    false,
                ));
            }
            directories.push(fs::read_dir(&path).map_err(io_error)?);
        } else if path
            .file_name()
            .and_then(|name| name.to_str())
            .is_some_and(|name| name.ends_with(".partial"))
        {
            visitor(&path)?;
        }
    }
    Ok(())
}
