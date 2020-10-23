package outcome;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor(access = PRIVATE)
@Getter
@Slf4j
public class Outcome<T> {

    public static Outcome<Void> success() {
        return new Outcome<>(true, null, null, null);
    }

    public static <T> Outcome<T> with(T value) {
        return new Outcome<>(true, value, null, null);
    }

    public static <T> Outcome<T> of(Supplier<T> valueSupplier) {
        return Outcome.success()
                      .onSuccessMap(v -> valueSupplier.get());
    }

    public static <T> Outcome<T> failure(int code, String message) {
        return failure(FailureMeta.of(code, message), null);
    }

    private static <T> Outcome<T> failure(FailureMeta failureMeta, Exception ex) {
        return new Outcome<>(false, null, failureMeta, ex);
    }

    private static <T> Outcome<T> failureWith(Exception ex) {
        return new Outcome<>(false, null, FailureMeta.of(0, ex.getLocalizedMessage()), ex);
    }

    private final boolean success;
    private final T value;
    private final FailureMeta failureMeta;
    private final Exception exceptionCaught;

    public <R> Outcome<R> onSuccessMap(Function<T, R> valueMapper) {
        return onSuccessFlatMap(valueMapper.andThen(Outcome::with));
    }

    public  <R> Outcome<R> onSuccessFlatMap(Function<T, Outcome<R>> outcomeMapper) {
        return success
                ? safelyInvoke(outcomeMapper)
                : failure(failureMeta, exceptionCaught);
    }

    public Outcome<T> onExceptionCustomize(Supplier<FailureMeta> failureMetaSupplier) {
        return (exceptionCaught == null)
                ? this
                : failure(failureMetaSupplier.get(), exceptionCaught);
    }

    public Outcome<T> peek(Consumer<Outcome<T>> outcomeConsumer) {
        try {
            outcomeConsumer.accept(this);
        } catch (Exception e) {
            log.info("Outcome consumer invoked in 'peek' method thrown exception", e);
        }
        return this;
    }

    private <R> Outcome<R> safelyInvoke(Function<T, Outcome<R>> outcomeMapper) {
        try {
            return outcomeMapper.apply(value);
        } catch (Exception e) {
            return failureWith(e);
        }
    }

    @Value(staticConstructor = "of")
    public static class FailureMeta {
        int code;
        String message;
    }
}

