// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.collections.Tuple2;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

@SuppressWarnings("deprecation")
public class ReplyMergerTestCase {

    private ReplyMerger merger;

    @Before
    public void setUp() {
        merger = new ReplyMerger();
    }

    @Test
    public void mergingGenericRepliesWithNoErrorsPicksFirstReply() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Reply r3 = new EmptyReply();
        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();

        assertThat(ret.first, is(0));
        assertThat(ret.second, sameInstance(r1));
    }

    @Test
    public void mergingSingleReplyWithOneErrorReturnsEmptyReplyWithError() {
        Reply r1 = new EmptyReply();
        Error error = new Error(1234, "oh no!");
        r1.addError(error);
        merger.merge(0, r1);
        Tuple2<Integer, Reply> ret = merger.mergedReply();

        assertThat(ret.first, nullValue());
        assertThat(ret.second, not(sameInstance(r1)));
        assertThatErrorsMatch(new Error[] { error }, ret);
    }

    @Test
    public void mergingSingleReplyWithMultipleErrorsReturnsEmptyReplyWithAllErrors() {
        Reply r1 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(1234, "oh no!"), new Error(4567, "oh dear!"),
        };
        r1.addError(errors[0]);
        r1.addError(errors[1]);
        merger.merge(0, r1);
        Tuple2<Integer, Reply> ret = merger.mergedReply();

        assertThat(ret.first, nullValue());
        assertThat(ret.second, not(sameInstance(r1)));
        assertThatErrorsMatch(errors, ret);
    }

    @Test
    public void mergingMultipleRepliesWithMultipleErrorsReturnsEmptyReplyWithAllErrors() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(1234, "oh no!"), new Error(4567, "oh dear!"), new Error(678, "omg!"),
        };
        r1.addError(errors[0]);
        r1.addError(errors[1]);
        r2.addError(errors[2]);
        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();

        assertThat(ret.first, nullValue());
        assertThat(ret.second, not(sameInstance(r1)));
        assertThat(ret.second, not(sameInstance(r2)));
        assertThatErrorsMatch(errors, ret);
    }

    @Test
    public void returnIgnoredReplyWhenAllRepliesHaveOnlyIgnoredErrors() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "oh no!"),
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "oh dear!"),
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "omg!"),
        };
        r1.addError(errors[0]);
        r1.addError(errors[1]);
        r2.addError(errors[2]);

        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertThat(ret.first, nullValue());
        assertThat(ret.second, not(sameInstance(r1)));
        assertThat(ret.second, not(sameInstance(r2)));
        // Only first ignore error from each reply
        assertThatErrorsMatch(new Error[]{ errors[0], errors[2] }, ret);
    }

    @Test
    public void successfulReplyTakesPrecedenceOverIgnoredReplyWhenNoErrors() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "oh no!"),
        };
        r1.addError(errors[0]);
        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertThat(ret.first, is(1));
        assertThat(ret.second, sameInstance(r2));
        // Only first ignore error from each reply
        assertThatErrorsMatch(new Error[]{ }, ret);
    }

    @Test
    public void nonIgnoredErrorTakesPrecedence() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "oh no!"),
                new Error(DocumentProtocol.ERROR_ABORTED, "kablammo!"),
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "omg!"),
        };
        r1.addError(errors[0]);
        r1.addError(errors[1]);
        r2.addError(errors[2]);

        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertThat(ret.first, nullValue());
        assertThat(ret.second, not(sameInstance(r1)));
        assertThat(ret.second, not(sameInstance(r2)));
        // All errors from replies with errors are included, not those that
        // are fully ignored.
        assertThatErrorsMatch(new Error[]{ errors[0], errors[1] }, ret);
    }

    @Test
    public void returnRemoveDocumentReplyWhereDocWasFound() {
        RemoveDocumentReply r1 = new RemoveDocumentReply();
        RemoveDocumentReply r2 = new RemoveDocumentReply();
        RemoveDocumentReply r3 = new RemoveDocumentReply();
        r1.setWasFound(false);
        r2.setWasFound(true);
        r3.setWasFound(false);

        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertThat(ret.first, is(1));
        assertThat(ret.second, sameInstance((Reply) r2));
    }

    @Test
    public void returnFirstRemoveDocumentReplyIfNoDocsWereFound() {
        RemoveDocumentReply r1 = new RemoveDocumentReply();
        RemoveDocumentReply r2 = new RemoveDocumentReply();
        r1.setWasFound(false);
        r2.setWasFound(false);

        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertThat(ret.first, is(0));
        assertThat(ret.second, sameInstance((Reply)r1));
    }

    @Test
    public void returnUpdateDocumentReplyWhereDocWasFound() {
        UpdateDocumentReply r1 = new UpdateDocumentReply();
        UpdateDocumentReply r2 = new UpdateDocumentReply();
        UpdateDocumentReply r3 = new UpdateDocumentReply();
        r1.setWasFound(false);
        r2.setWasFound(true); // return first reply
        r3.setWasFound(true);

        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertThat(ret.first, is(1));
        assertThat(ret.second, sameInstance((Reply)r2));
    }

    @Test
    public void returnGetDocumentReplyWhereDocWasFound() {
        GetDocumentReply r1 = new GetDocumentReply(null);
        GetDocumentReply r2 = new GetDocumentReply(null);
        GetDocumentReply r3 = new GetDocumentReply(null);
        r2.setLastModified(12345L);

        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertThat(ret.first, is(1));
        assertThat(ret.second, sameInstance((Reply)r2));
    }

    @Test
    public void mergingZeroRepliesReturnsDefaultEmptyReply() {
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertThat(ret.first, nullValue());
        assertThat(ret.second, instanceOf(EmptyReply.class));
        assertThatErrorsMatch(new Error[]{}, ret);
    }

    private void assertThatErrorsMatch(Error[] errors, Tuple2<Integer, Reply> ret) {
        assertThat(ret.second.getNumErrors(), is(errors.length));
        for (int i = 0; i < ret.second.getNumErrors(); ++i) {
            assertThat(ret.second.getError(i).getCode(), is(errors[i].getCode()));
            assertThat(ret.second.getError(i).getMessage(), is(errors[i].getMessage()));
        }
    }

}
