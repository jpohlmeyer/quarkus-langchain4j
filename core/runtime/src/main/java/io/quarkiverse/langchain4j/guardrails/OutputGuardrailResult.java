package io.quarkiverse.langchain4j.guardrails;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The result of the validation of an {@link OutputGuardrail}
 *
 * @param result The result of the output guardrail validation.
 * @param failures The list of failures, empty if the validation succeeded.
 */
public record OutputGuardrailResult(Result result, String successfulResult,
        List<Failure> failures) implements GuardrailResult<OutputGuardrailResult> {

    private static final OutputGuardrailResult SUCCESS = new OutputGuardrailResult();

    private OutputGuardrailResult() {
        this(Result.SUCCESS, null, Collections.emptyList());
    }

    private OutputGuardrailResult(String successfulResult) {
        this(Result.SUCCESS_WITH_RESULT, successfulResult, Collections.emptyList());
    }

    OutputGuardrailResult(List<Failure> failures, boolean fatal) {
        this(fatal ? Result.FATAL : Result.FAILURE, null, failures);
    }

    public static OutputGuardrailResult success() {
        return SUCCESS;
    }

    public static OutputGuardrailResult successWith(String successfulResult) {
        return new OutputGuardrailResult(successfulResult);
    }

    public static OutputGuardrailResult failure(List<? extends GuardrailResult.Failure> failures) {
        return new OutputGuardrailResult((List<Failure>) failures, false);
    }

    @Override
    public boolean isSuccess() {
        return result == Result.SUCCESS || result == Result.SUCCESS_WITH_RESULT;
    }

    @Override
    public boolean isRewrittenResult() {
        return result == Result.SUCCESS_WITH_RESULT;
    }

    public boolean isRetry() {
        return !isSuccess() && failures.stream().anyMatch(Failure::retry);
    }

    public OutputGuardrailResult blockRetry() {
        failures().set(0, failures().get(0).blockRetry());
        return this;
    }

    public String getReprompt() {
        if (!isSuccess()) {
            for (Failure failure : failures) {
                if (failure.reprompt() != null) {
                    return failure.reprompt();
                }
            }
        }
        return null;
    }

    @Override
    public boolean isFatal() {
        return result == Result.FATAL;
    }

    @Override
    public OutputGuardrailResult validatedBy(Class<? extends Guardrail> guardrailClass) {
        if (!isSuccess()) {
            if (failures.size() != 1) {
                throw new IllegalArgumentException();
            }
            failures.set(0, failures.get(0).withGuardrailClass(guardrailClass));
        }
        return this;
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "success";
        }
        return failures.stream().map(Failure::toString).collect(Collectors.joining(", "));
    }

    record Failure(String message, Throwable cause, Class<? extends Guardrail> guardrailClass, boolean retry,
            String reprompt) implements GuardrailResult.Failure {
        public Failure(String message) {
            this(message, null);
        }

        public Failure(String message, Throwable cause) {
            this(message, cause, false);
        }

        public Failure(String message, Throwable cause, boolean retry) {
            this(message, cause, null, retry, null);
        }

        public Failure(String message, Throwable cause, boolean retry, String reprompt) {
            this(message, cause, null, retry, reprompt);
        }

        @Override
        public Failure withGuardrailClass(Class<? extends Guardrail> guardrailClass) {
            return new Failure(message(), cause(), guardrailClass, retry, reprompt);
        }

        public Failure blockRetry() {
            return retry
                    ? new Failure("Retry or reprompt is not allowed after a rewritten output", cause(), guardrailClass, false,
                            reprompt)
                    : this;
        }

        @Override
        public String toString() {
            return "The guardrail " + guardrailClass.getName() + " failed with this message: " + message;
        }

    }
}
