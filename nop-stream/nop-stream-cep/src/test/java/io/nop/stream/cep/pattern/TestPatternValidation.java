package io.nop.stream.cep.pattern;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.pattern.conditions.IterativeCondition;
import io.nop.stream.cep.pattern.conditions.RichAndCondition;
import io.nop.stream.cep.pattern.conditions.RichOrCondition;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.cep.pattern.conditions.SubtypeCondition;
import io.nop.stream.cep.SubEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

public class TestPatternValidation {

    @Test
    void testStrictContiguity() {
        Pattern<Object, ?> pattern = Pattern.begin("start").next("next").next("end");
        Pattern<Object, ?> previous = pattern.getPrevious();
        Pattern<Object, ?> previous2 = previous.getPrevious();

        assertNotNull(previous);
        assertNotNull(previous2);
        assertNull(previous2.getPrevious());

        assertEquals("end", pattern.getName());
        assertEquals("next", previous.getName());
        assertEquals("start", previous2.getName());
    }

    @Test
    void testNonStrictContiguity() {
        Pattern<Object, ?> pattern = Pattern.begin("start").followedBy("next").followedBy("end");
        Pattern<Object, ?> previous = pattern.getPrevious();
        Pattern<Object, ?> previous2 = previous.getPrevious();

        assertNotNull(previous);
        assertNotNull(previous2);
        assertNull(previous2.getPrevious());

        assertEquals(Quantifier.ConsumingStrategy.SKIP_TILL_NEXT,
                pattern.getQuantifier().getConsumingStrategy());
        assertEquals(Quantifier.ConsumingStrategy.SKIP_TILL_NEXT,
                previous.getQuantifier().getConsumingStrategy());

        assertEquals("end", pattern.getName());
        assertEquals("next", previous.getName());
        assertEquals("start", previous2.getName());
    }

    @Test
    void testStrictContiguityWithCondition() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .next("next")
                .where(SimpleCondition.of((Event value) -> value.getName().equals("foobar")))
                .next("end")
                .where(SimpleCondition.of((Event value) -> value.getId() == 42));

        assertNotNull(pattern.getPrevious());
        assertNotNull(pattern.getPrevious().getPrevious());
        assertNull(pattern.getPrevious().getPrevious().getPrevious());

        assertNotNull(pattern.getCondition());
        assertNotNull(pattern.getPrevious().getCondition());
    }

    @Test
    void testPatternWithSubtyping() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .next("subevent")
                .subtype(SubEvent.class)
                .followedBy("end");

        Pattern<Event, ?> previous = pattern.getPrevious();
        assertNotNull(previous);
        assertNotNull(previous.getCondition());
        assertTrue(previous.getCondition() instanceof SubtypeCondition);
    }

    @Test
    void testPatternWithSubtypingAndFilter() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .next("subevent")
                .subtype(SubEvent.class)
                .where(SimpleCondition.of(value -> false))
                .followedBy("end");

        Pattern<Event, ?> previous = pattern.getPrevious();
        assertNotNull(previous);
        assertNotNull(previous.getCondition());
        assertEquals(Quantifier.ConsumingStrategy.SKIP_TILL_NEXT,
                pattern.getQuantifier().getConsumingStrategy());
    }

    @Test
    void testPatternWithOrFilter() {
        Pattern<Event, Event> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of((Event value) -> false))
                .or(SimpleCondition.of((Event value) -> false))
                .next("or")
                .or(SimpleCondition.of((Event value) -> false))
                .followedBy("end");

        Pattern<Event, ?> previous = pattern.getPrevious();
        Pattern<Event, ?> previous2 = previous.getPrevious();

        assertNotNull(previous);
        assertNotNull(previous2);

        assertFalse(previous.getCondition() instanceof RichOrCondition);
        assertTrue(previous2.getCondition() instanceof RichOrCondition);
    }

    @Test
    void testRichCondition() {
        Pattern<Object, Object> pattern = Pattern.begin("start")
                .where(dummyCondition())
                .where(dummyCondition())
                .followedBy("end")
                .where(dummyCondition())
                .or(dummyCondition());
        assertTrue(pattern.getCondition() instanceof RichOrCondition);
        assertTrue(pattern.getPrevious().getCondition() instanceof RichAndCondition);
    }

    @Test
    void testPatternTimesNegativeTimes() {
        assertThrows(IllegalArgumentException.class, () ->
                Pattern.begin("start").where(dummyCondition()).times(-1));
    }

    @Test
    void testPatternTimesNegativeFrom() {
        assertThrows(IllegalArgumentException.class, () ->
                Pattern.begin("start").where(dummyCondition()).times(-1, 2));
    }

    @Test
    void testPatternCanHaveQuantifierSpecifiedOnce1() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start").where(dummyCondition()).oneOrMore().oneOrMore().optional());
    }

    @Test
    void testPatternCanHaveQuantifierSpecifiedOnce2() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start").where(dummyCondition()).oneOrMore().optional().times(1));
    }

    @Test
    void testPatternCanHaveQuantifierSpecifiedOnce3() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start").where(dummyCondition()).times(1).oneOrMore());
    }

    @Test
    void testPatternCanHaveQuantifierSpecifiedOnce4() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start").where(dummyCondition()).oneOrMore().oneOrMore());
    }

    @Test
    void testNotNextCannotBeOneOrMore() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start")
                        .where(dummyCondition())
                        .notNext("not")
                        .where(dummyCondition())
                        .oneOrMore());
    }

    @Test
    void testNotNextCannotBeTimes() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start")
                        .where(dummyCondition())
                        .notNext("not")
                        .where(dummyCondition())
                        .times(3));
    }

    @Test
    void testNotNextCannotBeOptional() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start")
                        .where(dummyCondition())
                        .notNext("not")
                        .where(dummyCondition())
                        .optional());
    }

    @Test
    void testNotFollowedCannotBeOneOrMore() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start")
                        .where(dummyCondition())
                        .notFollowedBy("not")
                        .where(dummyCondition())
                        .oneOrMore());
    }

    @Test
    void testNotFollowedCannotBeTimes() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start")
                        .where(dummyCondition())
                        .notFollowedBy("not")
                        .where(dummyCondition())
                        .times(3));
    }

    @Test
    void testNotFollowedCannotBeOptional() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start")
                        .where(dummyCondition())
                        .notFollowedBy("not")
                        .where(dummyCondition())
                        .optional());
    }

    @Test
    void testUntilCanBeAppliedToTimes() {
        assertDoesNotThrow(() ->
                Pattern.begin("start").where(dummyCondition()).times(1).until(dummyCondition()));
    }

    @Test
    void testUntilCannotBeAppliedToSingleton() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start").where(dummyCondition()).until(dummyCondition()));
    }

    @Test
    void testUntilCannotBeAppliedTwice() {
        assertThrows(MalformedPatternException.class, () ->
                Pattern.begin("start")
                        .where(dummyCondition())
                        .oneOrMore()
                        .until(dummyCondition())
                        .until(dummyCondition()));
    }

    @Test
    void testFollowedByAnyConsumingStrategy() {
        Pattern<Object, ?> pattern = Pattern.begin("a").followedByAny("b");
        assertEquals(Quantifier.ConsumingStrategy.SKIP_TILL_ANY,
                pattern.getQuantifier().getConsumingStrategy());
    }

    @Test
    void testNotNextConsumingStrategy() {
        Pattern<Object, ?> pattern = Pattern.begin("a").notNext("b");
        assertEquals(Quantifier.ConsumingStrategy.NOT_NEXT,
                pattern.getQuantifier().getConsumingStrategy());
    }

    @Test
    void testNotFollowedByConsumingStrategy() {
        Pattern<Object, ?> pattern = Pattern.begin("a").notFollowedBy("b");
        assertEquals(Quantifier.ConsumingStrategy.NOT_FOLLOW,
                pattern.getQuantifier().getConsumingStrategy());
    }

    @Test
    void testOneOrMoreQuantifierProperty() {
        Pattern<Object, ?> pattern = Pattern.begin("a").where(dummyCondition()).oneOrMore();
        assertTrue(pattern.getQuantifier().hasProperty(Quantifier.QuantifierProperty.LOOPING));
        assertFalse(pattern.getQuantifier().hasProperty(Quantifier.QuantifierProperty.SINGLE));
    }

    @Test
    void testTimesQuantifierProperty() {
        Pattern<Object, ?> pattern = Pattern.begin("a").where(dummyCondition()).times(3);
        assertTrue(pattern.getQuantifier().hasProperty(Quantifier.QuantifierProperty.TIMES));
        assertEquals(3, pattern.getTimes().getFrom());
        assertEquals(3, pattern.getTimes().getTo());
    }

    @Test
    void testTimesRange() {
        Pattern<Object, ?> pattern = Pattern.begin("a").where(dummyCondition()).times(2, 5);
        assertEquals(2, pattern.getTimes().getFrom());
        assertEquals(5, pattern.getTimes().getTo());
    }

    @Test
    void testOptionalProperty() {
        Pattern<Object, ?> pattern = Pattern.begin("a")
                .where(dummyCondition())
                .oneOrMore()
                .optional();
        assertTrue(pattern.getQuantifier().hasProperty(Quantifier.QuantifierProperty.OPTIONAL));
    }

    @Test
    void testConsecutive() {
        Pattern<Object, ?> pattern = Pattern.begin("a")
                .where(dummyCondition())
                .oneOrMore()
                .consecutive();
        assertEquals(Quantifier.ConsumingStrategy.STRICT,
                pattern.getQuantifier().getInnerConsumingStrategy());
    }

    @Test
    void testAllowCombinations() {
        Pattern<Object, ?> pattern = Pattern.begin("a")
                .where(dummyCondition())
                .oneOrMore()
                .allowCombinations();
        assertEquals(Quantifier.ConsumingStrategy.SKIP_TILL_ANY,
                pattern.getQuantifier().getInnerConsumingStrategy());
    }

    @Test
    void testTimesOrMore() {
        Pattern<Object, ?> pattern = Pattern.begin("a").where(dummyCondition()).timesOrMore(3);
        assertTrue(pattern.getQuantifier().hasProperty(Quantifier.QuantifierProperty.LOOPING));
        assertEquals(3, pattern.getTimes().getFrom());
    }

    private SimpleCondition<Object> dummyCondition() {
        return SimpleCondition.of(value -> true);
    }
}
