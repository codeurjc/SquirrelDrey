package es.codeurjc.squirrel.drey.local.autoscaling;

public class AutoScalingException extends Exception {

    private static final long serialVersionUID = 1L;

    public AutoScalingException() {
        super();
    }
    public AutoScalingException(String message) {
        super(message);
    }

    public AutoScalingException(String message, Throwable cause) {
        super(message, cause);
    }
    public AutoScalingException(Throwable cause) {
        super(cause);
    }

}
