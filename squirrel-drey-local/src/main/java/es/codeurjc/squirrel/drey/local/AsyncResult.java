package es.codeurjc.squirrel.drey.local;

import java.io.Serializable;

public class AsyncResult<E> implements Serializable {

    private String operationId;
    private E result;

    public AsyncResult(String operationId, E result) {
        this.operationId = operationId;
        this.result = result;
    }

    public String getOperationId() {
        return operationId;
    }

    public E getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "AsyncResult [" +
                "operationId='" + operationId + '\'' +
                ", result=" + result + "]";
    }
}
