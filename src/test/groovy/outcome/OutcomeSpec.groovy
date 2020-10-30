package outcome

import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Unroll

import static Outcome.FailureMeta

class OutcomeSpec extends Specification {

    def "should create success outcome without value and failure meta"() {
        when:
        def outcome = Outcome.success()

        then:
        assertSuccess(outcome, null)
    }

    @Unroll
    def "should create success outcome with value and without failure meta"() {
        when:
        def outcome = Outcome.with(value)

        then:
        assertSuccess(outcome, value)

        where:
        value             | _
        null              | _
        'non null object' | _
    }

    @Unroll
    def "should create failure outcome without value and with failure meta containing only message and code"() {
        when:
        def code = 123
        def outcome = Outcome.failure(code, message)

        then:
        assertFailureWithoutException(outcome, code, message)

        where:
        message             | _
        null                | _
        'non empty message' | _
    }

    def "should create failure outcome without value and with failure meta containing only message"() {
        when:
        String message = 'message'
        def outcome = Outcome.failure(message)

        then:
        assertFailureWithoutException(outcome, 0, message)
    }

    def "should create success outcome with value from non-failing supplier"() {
        when:
        def value = "value"
        def outcome = Outcome.of({ -> value })

        then:
        assertSuccess(outcome, value)
    }

    def "should create failure outcome with failure meta from failing supplier"() {
        when:
        def exceptionMessage = "Exception thrown"
        def exception = new RuntimeException(exceptionMessage)
        def outcome = Outcome.of({ -> throw exception })

        then:
        assertFailureWithException(outcome, 0, exceptionMessage, exception)
    }

    def "should map value of success outcome"() {
        given:
        def success = Outcome.with(1)
        def additionalValue = 1

        when:
        def mappedOutcome = success.onSuccessMap({ currentValue -> currentValue + additionalValue })

        then:
        assertSuccess(mappedOutcome, 2)
    }

    def "should map success to failure when mapping throws an exception"() {
        given:
        def exceptionMessage = "Exception thrown"
        def exception = new RuntimeException(exceptionMessage)
        def success = Outcome.with('non empty value')

        when:
        def mappedOutcome = success.onSuccessMap({ currentValue -> throw exception })

        then:
        assertFailureWithException(mappedOutcome, 0, exceptionMessage, exception)
    }

    def "should not map value of failure outcome"() {
        given:
        def code = 123
        def message = 'message'
        def failure = Outcome.failure(code, message)

        when:
        def mappedOutcome = failure.onSuccessMap({ currentValue -> 'newValue' })

        then:
        assertFailureWithoutException(mappedOutcome, code, message)
    }

    def "should flatmap success outcome to another success outcome"() {
        given:
        def successOutcome = Outcome.with(1)
        def additionalValue = 1

        when:
        def mappedOutcome = successOutcome.onSuccessFlatMap({ currentValue -> Outcome.with(currentValue + additionalValue) })

        then:
        assertSuccess(mappedOutcome, 2)
    }

    def "should flatmap success outcome to failure outcome"() {
        given:
        def success = Outcome.with(1)
        def code = 123
        def message = 'message'

        when:
        def mappedOutcome = success.onSuccessFlatMap({ currentValue -> Outcome.failure(code, message) })

        then:
        assertFailureWithoutException(mappedOutcome, code, message)
    }

    def "should flatmap success outcome to failure outcome when operation throws exception"() {
        given:
        def exceptionMessage = "Exception thrown"
        def exception = new RuntimeException(exceptionMessage)
        def success = Outcome.with('non empty value')

        when:
        def mappedOutcome = success.onSuccessFlatMap({ currentValue -> throw exception })

        then:
        assertFailureWithException(mappedOutcome, 0, exceptionMessage, exception)
    }

    def "should not flatmap failure outcome"() {
        given:
        def code = 123
        def message = 'message'
        def failure = Outcome.failure(code, message)

        when:
        def mappedOutcome = failure.onSuccessFlatMap({ currentValue -> Outcome.with('new value') })

        then:
        assertFailureWithoutException(mappedOutcome, code, message)
    }

    def "should map outcome on exception when exception was thrown"() {
        given:
        def exceptionMessage = "Exception thrown"
        def exception = new RuntimeException(exceptionMessage)
        def failureWithException = Outcome.success().onSuccessMap({ currentValue -> throw exception })
        def newMessage = 'new message'
        def newCode = 456

        when:
        def mappedOutcome = failureWithException.onExceptionCustomize({ FailureMeta.of(newCode, newMessage) })

        then:
        assertFailureWithException(mappedOutcome, newCode, newMessage, exception)
    }

    def "should not map outcome on exception when exception was not thrown"() {
        given:
        def code = 123
        def message = 'message'
        def failure = Outcome.failure(code, message)
        def newMessage = 'new message'
        def newCode = 456

        when:
        def mappedOutcome = failure.onExceptionCustomize({ FailureMeta.of(newCode, newMessage) })

        then:
        assertFailureWithoutException(mappedOutcome, code, message)
    }

    @Unroll
    def "should peek current outcome and does not change it's state"() {
        when:
        Outcome o = currentOutcome
        def mappedOutcome = o.peek({ outcome -> })

        then:
        assertOutcomesEqual(currentOutcome, mappedOutcome)

        where:
        currentOutcome             | _
        Outcome.with('non empty value')         | _
        Outcome.failure(123, 'non empty value') | _
    }

    @Unroll
    def "should log info from current outcome and does not change it's state"() {
        given:
        Outcome o = currentOutcome
        String messagePrefix = "Prefix"

        when:
        def mappedOutcome = o.log(messagePrefix)

        then:
        assertOutcomesEqual(currentOutcome, mappedOutcome)

        where:
        currentOutcome             | _
        Outcome.with('non empty value')         | _
        Outcome.failure(123, 'non empty value') | _
    }

    def "should log info from success and does not change it's state"() {
        given:
        Outcome outcome = Outcome.with('non empty value')
        Logger logger = Mock()
        String messagePrefix = "Prefix"

        when:
        def mappedOutcome = outcome.log(logger, messagePrefix)

        then:
        assertOutcomesEqual(outcome, mappedOutcome)
        1 * logger.info(messagePrefix + ": Success")
    }

    def "should log info from custom failure and does not change it's state"() {
        given:
        int code = 123
        String message = "message"
        Outcome outcome = Outcome.failure(code, message)
        Logger logger = Mock()
        String messagePrefix = "Prefix"

        when:
        def mappedOutcome = outcome.log(logger, messagePrefix)

        then:
        assertOutcomesEqual(outcome, mappedOutcome)
        1 * logger.info("$messagePrefix: Failure. Code: $code, message: $message", null)
    }

    def "should log info from exception failure and does not change it's state"() {
        given:
        String message = "message"
        def exception = new RuntimeException(message)
        def failureWithException = Outcome.success().onSuccessMap({ currentValue -> throw exception })
        Logger logger = Mock()
        String messagePrefix = "Prefix"

        when:
        def mappedOutcome = failureWithException.log(logger, messagePrefix)

        then:
        assertOutcomesEqual(failureWithException, mappedOutcome)
        1 * logger.info("$messagePrefix: Failure. Code: 0, message: $message", exception)
    }

    private static void assertSuccess(Outcome success, Object value) {
        assert success
        assert success.success
        assert success.value == value
        assert !success.failureMeta
        assert !success.exceptionCaught
    }

    private static void assertFailure(Outcome failure, int code, String message) {
        assert failure
        assert !failure.success
        assert !failure.value
        assert failure.failureMeta
        assert failure.failureMeta.message == message
        assert failure.failureMeta.code == code
    }

    private static void assertFailureWithoutException(Outcome failure, int code, String message) {
        assertFailure(failure, code, message)
        assert !failure.exceptionCaught
    }

    private static void assertFailureWithException(Outcome failure, int code, String message, Exception exception) {
        assertFailure(failure, code, message)
        assert failure.exceptionCaught == exception
    }

    private static void assertOutcomesEqual(Outcome expected, Outcome actual) {
        assert actual.isSuccess() == expected.isSuccess()
        assert actual.getFailureMeta()?.getMessage() == expected.getFailureMeta()?.getMessage()
        assert actual.getFailureMeta()?.getCode() == expected.getFailureMeta()?.getCode()
        assert actual.getExceptionCaught() == expected.getExceptionCaught()
    }
}
