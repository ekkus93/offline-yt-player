use crate::domain::{CoreError, ErrorKind};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum DownloadState {
    Queued,
    Resolving,
    Downloading,
    Paused,
    RetryWait,
    Failed,
    Verifying,
    Completed,
    Canceled,
}

impl DownloadState {
    #[must_use]
    pub const fn is_terminal(self) -> bool {
        matches!(self, Self::Failed | Self::Completed | Self::Canceled)
    }

    #[must_use]
    pub const fn can_transition_to(self, next: Self) -> bool {
        use DownloadState::{
            Canceled, Completed, Downloading, Failed, Paused, Queued, Resolving, RetryWait,
            Verifying,
        };
        matches!(
            (self, next),
            (Queued, Resolving | Canceled)
                | (
                    Resolving,
                    Downloading | Paused | Failed | RetryWait | Canceled
                )
                | (
                    Downloading,
                    Paused | RetryWait | Failed | Verifying | Canceled
                )
                | (Paused, Queued | Canceled)
                | (RetryWait, Resolving | Downloading | Failed | Canceled)
                | (Failed, Queued | Resolving | Canceled)
                | (
                    Verifying,
                    Paused | Completed | Failed | RetryWait | Canceled
                )
        )
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DownloadStateMachine {
    state: DownloadState,
}

impl Default for DownloadStateMachine {
    fn default() -> Self {
        Self {
            state: DownloadState::Queued,
        }
    }
}

impl DownloadStateMachine {
    #[must_use]
    pub const fn new(state: DownloadState) -> Self {
        Self { state }
    }

    #[must_use]
    pub const fn state(&self) -> DownloadState {
        self.state
    }

    pub fn transition(&mut self, next: DownloadState) -> Result<(), CoreError> {
        if !self.state.can_transition_to(next) {
            return Err(CoreError::new(
                ErrorKind::Internal,
                format!("illegal download transition: {:?} -> {next:?}", self.state),
                false,
            ));
        }
        self.state = next;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const STATES: [DownloadState; 9] = [
        DownloadState::Queued,
        DownloadState::Resolving,
        DownloadState::Downloading,
        DownloadState::Paused,
        DownloadState::RetryWait,
        DownloadState::Failed,
        DownloadState::Verifying,
        DownloadState::Completed,
        DownloadState::Canceled,
    ];

    #[test]
    fn every_state_pair_matches_transition_contract() {
        for from in STATES {
            for to in STATES {
                let expected = from.can_transition_to(to);
                let mut machine = DownloadStateMachine::new(from);
                let actual = machine.transition(to).is_ok();
                assert_eq!(actual, expected, "transition {from:?} -> {to:?}");
                if actual {
                    assert_eq!(machine.state(), to);
                } else {
                    assert_eq!(machine.state(), from);
                }
            }
        }
    }

    #[test]
    fn completed_and_canceled_are_terminal() {
        for state in [DownloadState::Completed, DownloadState::Canceled] {
            for next in STATES {
                assert!(!state.can_transition_to(next));
            }
        }
    }

    #[test]
    fn active_jobs_can_pause_without_becoming_terminal() {
        assert!(DownloadState::Resolving.can_transition_to(DownloadState::Paused));
        assert!(DownloadState::Downloading.can_transition_to(DownloadState::Paused));
        assert!(DownloadState::Verifying.can_transition_to(DownloadState::Paused));
        assert!(!DownloadState::Paused.is_terminal());
    }

    #[test]
    fn paused_job_resumes_by_reentering_eligible_queue() {
        assert!(DownloadState::Paused.can_transition_to(DownloadState::Queued));
        assert!(!DownloadState::Paused.can_transition_to(DownloadState::Downloading));
    }

    #[test]
    fn failed_job_can_retry_but_not_jump_to_downloading() {
        assert!(DownloadState::Failed.can_transition_to(DownloadState::Queued));
        assert!(DownloadState::Failed.can_transition_to(DownloadState::Resolving));
        assert!(!DownloadState::Failed.can_transition_to(DownloadState::Downloading));
    }
}
