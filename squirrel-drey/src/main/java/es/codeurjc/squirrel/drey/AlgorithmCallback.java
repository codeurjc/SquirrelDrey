package es.codeurjc.squirrel.drey;

public interface AlgorithmCallback<R> {

	void onSuccess(R result, Algorithm<R> algorithm);

	void onError(Algorithm<R> algorithm);

}