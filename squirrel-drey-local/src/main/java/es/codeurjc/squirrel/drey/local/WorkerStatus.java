package es.codeurjc.squirrel.drey.local;

public enum WorkerStatus {

    /**
     * Worker is launching
     */
    launching {
        public String toString() {
            return "launching";
        }
    },

    /**
     * Worker instance is launching, but before it enters running state it will
     * be automatically set to terminating state. This allows flagging a launching
     * Worker to be dropped immediately after it reaches a stable state, giving
     * no opportunity for a task to be initialized inside of it
     */
    canceled {
        public String toString() {
            return "canceled";
        }
    },

    /**
     * Worker instance failed to launch. This status can be reached from
     * launching status.
     */
    failed {
        public String toString() {
            return "failed";
        }
    },

    /**
     * Worker instance is up and running. This status can be reached from
     * launching and waitingIdleToTerminate status
     */
    running {
        public String toString() {
            return "running";
        }
    },

    /**
     * Worker instance is waiting until the last of its task is terminated. Once
     * this happens, it will automatically enter terminating status. It won't accept
     * new tasks during the wait. This status can be reached from running status
     */
    waitingIdleToTerminate {
        public String toString() {
            return "waiting-idle-to-terminate";
        }
    },

    /**
     * Worker instance is shutting down. This status can be reached from running
     * and waitingIdleToTerminate
     */
    terminating {
        public String toString() {
            return "terminating";
        }
    },

    /**
     * Worker instance is shut down. This status can be reached from terminating
     * status
     */
    terminated {
        public String toString() {
            return "terminated";
        }
    };

    public static WorkerStatus stringToStatus(String str) throws IllegalArgumentException {
        switch (str) {
            case "launching":
                return WorkerStatus.launching;
            case "canceled":
                return WorkerStatus.canceled;
            case "failed":
                return WorkerStatus.failed;
            case "running":
                return WorkerStatus.running;
            case "waiting-idle-to-terminate":
                return WorkerStatus.waitingIdleToTerminate;
            case "terminating":
                return WorkerStatus.terminating;
            case "terminated":
                return WorkerStatus.terminated;
            default:
                throw new IllegalArgumentException();
        }
    }

}
