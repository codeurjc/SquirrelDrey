package es.codeurjc.squirrel.drey.loadapp.task;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

import es.codeurjc.squirrel.drey.Task;

public class PiDigitsTask extends LoadTask {

	private static final long serialVersionUID = 1L;
	private int numberOfDigits;

	public PiDigitsTask(int numberOfDigits, int delay, int taskDuration, String parentTaskId, Task nextInsertionTask) {
		super(delay, taskDuration, parentTaskId, nextInsertionTask);
		this.numberOfDigits = numberOfDigits;
	}

	@Override
	public void process() throws Exception {

		super.runDelay();

		BigDecimal pi = BigDecimal.ZERO;
		BigDecimal denom1 = BigDecimal.ONE;
		BigDecimal denom2 = BigDecimal.ONE;
		BigDecimal term1 = BigDecimal.ZERO;
		BigDecimal term2 = BigDecimal.ZERO;

		// Nilakantha Series
		// pi = (3 + ((4/(i*(i+1)*(i+2))-(4/((i+2)*(i+3)*(i+4))))))...
		// Calculate denominators, terms and then pi

		for (int i = 2; i < numberOfDigits; i += 4) {
			denom1 = BigDecimal.ONE;
			denom1 = denom1.multiply(new BigDecimal(i));
			denom1 = denom1.multiply(new BigDecimal(i + 1));
			denom1 = denom1.multiply(new BigDecimal(i + 2));

			denom2 = BigDecimal.ONE;
			denom2 = denom2.multiply(new BigDecimal(i + 2));
			denom2 = denom2.multiply(new BigDecimal(i + 3));
			denom2 = denom2.multiply(new BigDecimal(i + 4));

			// Accuracy parameters set to 1000 arbitrarily
			term1 = new BigDecimal("4").divide(denom1, numberOfDigits, RoundingMode.HALF_UP);
			term2 = new BigDecimal("-4").divide(denom2, numberOfDigits, RoundingMode.HALF_UP);

			pi = pi.add(term1);
			pi = pi.add(term2);
		}

		// 3 + calculated value
		pi = pi.add(new BigDecimal(3));

		// Number of digits applied
		pi = pi.setScale(numberOfDigits, BigDecimal.ROUND_HALF_UP);

		Collection<BigDecimal> results = (Collection<BigDecimal>) this.getList("results");
		results.add(pi);

		super.addNewInsertionTaskIfNecessary();
	}

}
