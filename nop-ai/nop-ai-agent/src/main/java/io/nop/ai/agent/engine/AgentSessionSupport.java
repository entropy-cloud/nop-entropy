package io.nop.ai.agent.engine;

import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.MailboxMessageHandler;
import io.nop.ai.agent.model.AgentModel;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.core.resource.component.ResourceComponentManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Session-facility helpers for the engine (extracted from
 * {@link DefaultAgentEngine}, MA4.2-05): per-session mailbox creation with
 * handler registration, sessionId validation/fallback and agent-model
 * loading from the VFS resource registry.
 */
public class AgentSessionSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);
    private final AgentCallDelegate callDelegate;
    private final java.util.concurrent.ConcurrentHashMap<String, IMailbox> sessionMailboxes;
    private final java.util.concurrent.ConcurrentHashMap<String, IMessageSubscription> sessionMailboxSubscriptions;

    private Function<String, IMailbox> mailboxFactory;

    public AgentSessionSupport(AgentCallDelegate callDelegate,
                               java.util.concurrent.ConcurrentHashMap<String, IMailbox> sessionMailboxes,
                               java.util.concurrent.ConcurrentHashMap<String, IMessageSubscription> sessionMailboxSubscriptions) {
        this.callDelegate = callDelegate;
        this.sessionMailboxes = sessionMailboxes;
        this.sessionMailboxSubscriptions = sessionMailboxSubscriptions;
    }

    public void setMailboxFactory(Function<String, IMailbox> mailboxFactory) {
        this.mailboxFactory = mailboxFactory;
    }

    // ---- moved verbatim from DefaultAgentEngine (MA4.2-05 split) ----
    public Function<String, IMailbox> getMailboxFactory() {
        return mailboxFactory;
    }
    public void ensureSessionMailbox(String sessionId) {
        if (mailboxFactory == null) {
            return;
        }
        sessionMailboxes.computeIfAbsent(sessionId, sid -> {
            IMailbox mailbox = mailboxFactory.apply(sid);
            if (mailbox == null) {
                LOG.warn("DefaultAgentEngine: mailboxFactory returned null for sessionId={}, "
                        + "no mailbox will be created for this session", sid);
                return null;
            }
            String inboxTopic = AgentMessageTopics.inboxTopic(sid);
            MailboxMessageHandler handler = new MailboxMessageHandler(mailbox);
            IMessageSubscription subscription = callDelegate.getMessenger().registerHandler(inboxTopic, handler);
            if (subscription != null) {
                sessionMailboxSubscriptions.put(sid, subscription);
            }
            LOG.debug("DefaultAgentEngine: created mailbox for sessionId={}, registered handler on topic={}",
                    sid, inboxTopic);
            return mailbox;
        });
    }
    public String resolveSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            // P0 path-traversal guard (finding [13-15]): a caller-controlled
            // sessionId flows into Path.resolve(sessionId) in the file-backed
            // stores. Reject any id outside [A-Za-z0-9_-] before use. The
            // UUID fallback below produces a regex-safe id by construction.
            // Note: this covers execute/sendMessage only — resumeSession/
            // restoreSession/cancelSession bypass resolveSessionId and are
            // guarded by the store/checkpoint-layer containment check
            // (SessionIds.requireContainedPath).
            return SessionIds.requireValidIdentifier(sessionId);
        }
        return UUID.randomUUID().toString();
    }
    public AgentModel loadAgentModel(String agentName) {
        AgentNames.requireValidIdentifier(agentName);
        String path = "/" + agentName + ".agent.xml";
        try {
            Object obj = ResourceComponentManager.instance().loadComponentModel(path);
            if (!(obj instanceof AgentModel)) {
                throw new NopAiAgentException("Failed to load agent model from " + path
                        + ": unexpected type " + obj.getClass().getName());
            }
            return (AgentModel) obj;
        } catch (NopAiAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new NopAiAgentException("Failed to load agent model: agentName=" + agentName, e);
        }
    }

    public IMailbox getSessionMailbox(String sessionId) {
        return sessionMailboxes.get(sessionId);
    }
}
