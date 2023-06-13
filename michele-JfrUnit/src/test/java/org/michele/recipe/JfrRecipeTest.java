package org.michele.recipe;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class JfrRecipeTest implements RewriteTest {
	// Note, you can define defaults for the RecipeSpec and these defaults will be
	// used for all tests.
	@Override
	public void defaults(RecipeSpec spec) {
		spec.recipe(new JfrRecipe());
	}

	@Test
	void refactorTest() {
		rewriteRun(java("""
				package fr.lidonis.calculator.domain;

				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.params.ParameterizedTest;
				import org.junit.jupiter.params.provider.ValueSource;

				import java.math.BigDecimal;

				import static org.assertj.core.api.Assertions.assertThat;
				import static org.assertj.core.api.Assertions.catchThrowable;

				public class CalculatorImplTest {

				    @Test
				    protected void given_1_plus_1_return_2() {
				        BigDecimal result = new CalculatorImpl().evaluate("1+1");
				        assertThat(result).isEqualTo(new BigDecimal(2));
				    }
				}
								""", """
				package fr.lidonis.calculator.domain;

				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.params.ParameterizedTest;
				import org.junit.jupiter.params.provider.ValueSource;

				import java.math.BigDecimal;

				import static org.assertj.core.api.Assertions.assertThat;
				import static org.assertj.core.api.Assertions.catchThrowable;

				@org.moditect.jfrunit.JfrEventTest
				public class CalculatorImplTest {

					public org.moditect.jfrunit.JfrEvents jfrEvents = new org.moditect.jfrunit.JfrEvents();

					@org.moditect.jfrunit.EnableEvent("jdk.*")
					@Test
				    public void given_1_plus_1_return_2() {
					    BigDecimal result = new CalculatorImpl().evaluate("1+1");
					    assertThat(result).isEqualTo(new BigDecimal(2));
					    jfrEvents.awaitEvents();
					}
				}
												"""));
	}
}
