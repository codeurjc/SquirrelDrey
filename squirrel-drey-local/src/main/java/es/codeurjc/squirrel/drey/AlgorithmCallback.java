package es.codeurjc.squirrel.drey;

import java.io.Serializable;

public interface AlgorithmCallback<R extends Serializable> {

	void onSuccess(R result, Algorithm<R> algorithm);

	void onError(Algorithm<R> algorithm);

}