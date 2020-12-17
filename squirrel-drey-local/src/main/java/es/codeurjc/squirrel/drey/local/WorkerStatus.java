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
     * Worker instance is up and running. This status can be reached from
     * launching and waitingIdleToTerminate status
     */
    running {
        public String toString() {
            return "running";
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
    };

    public static WorkerStatus stringToStatus(String str) throws IllegalArgumentException {
        switch (str) {
            case "launching":
                return WorkerStatus.launching;
            case "running":
                return WorkerStatus.running;
            case "terminating":
                return WorkerStatus.terminating;
            default:
                throw new IllegalArgumentException();
        }
    }

}
