/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.jboss.as.console.client.shared.subsys.messaging;

import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.PlaceRequest;
import com.gwtplatform.mvp.client.proxy.Proxy;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.dispatch.DispatchAsync;
import org.jboss.as.console.client.shared.dispatch.impl.DMRAction;
import org.jboss.as.console.client.shared.dispatch.impl.DMRResponse;
import org.jboss.as.console.client.shared.model.ModelAdapter;
import org.jboss.as.console.client.shared.model.ResponseWrapper;
import org.jboss.as.console.client.shared.subsys.Baseadress;
import org.jboss.as.console.client.shared.subsys.RevealStrategy;
import org.jboss.as.console.client.shared.subsys.messaging.model.AddressingPattern;
import org.jboss.as.console.client.shared.subsys.messaging.model.ConnectionFactory;
import org.jboss.as.console.client.shared.subsys.messaging.model.JMSEndpoint;
import org.jboss.as.console.client.shared.subsys.messaging.model.MessagingProvider;
import org.jboss.as.console.client.shared.subsys.messaging.model.Queue;
import org.jboss.as.console.client.shared.subsys.messaging.model.SecurityPattern;
import org.jboss.as.console.client.shared.subsys.messaging.model.Topic;
import org.jboss.as.console.client.widgets.forms.AddressBinding;
import org.jboss.as.console.client.widgets.forms.EntityAdapter;
import org.jboss.as.console.client.widgets.forms.KeyAssignment;
import org.jboss.as.console.client.widgets.forms.PropertyBinding;
import org.jboss.as.console.client.widgets.forms.PropertyMetaData;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.Property;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * @author Heiko Braun
 * @date 5/10/11
 */
public class MessagingPresenter extends Presenter<MessagingPresenter.MyView, MessagingPresenter.MyProxy> {

    private final PlaceManager placeManager;
    private DispatchAsync dispatcher;
    private BeanFactory factory;
    private MessagingProvider providerEntity;
    private DefaultWindow window = null;
    private RevealStrategy revealStrategy;
    private PropertyMetaData metaData;
    private List<SecurityPattern> securitySettings = new ArrayList<SecurityPattern>();

    private EntityAdapter<MessagingProvider> providerAdapter;
    private EntityAdapter<SecurityPattern> securityAdapter;
    private EntityAdapter<AddressingPattern> addressingAdapter;
    private String currentServer = null;

    @ProxyCodeSplit
    @NameToken(NameTokens.MessagingPresenter)
    public interface MyProxy extends Proxy<MessagingPresenter>, Place {
    }

    public interface MyView extends View {

        // Messaging Provider
        void setPresenter(MessagingPresenter presenter);
        void setProviderDetails(MessagingProvider provider);
        void setSecurityConfig(List<SecurityPattern> secPatterns);
        void setAddressingConfig(List<AddressingPattern> addrPatterns);
    }

    public interface JMSView {
        void setQueues(List<Queue> queues);
        void setTopics(List<JMSEndpoint> topics);
        void setConnectionFactories(List<ConnectionFactory> factories);
        void enableEditQueue(boolean b);
        void enableEditTopic(boolean b);
    }

    @Inject
    public MessagingPresenter(
            EventBus eventBus, MyView view, MyProxy proxy,
            PlaceManager placeManager, DispatchAsync dispatcher,
            BeanFactory factory, RevealStrategy revealStrategy,
            PropertyMetaData propertyMetaData) {
        super(eventBus, view, proxy);

        this.placeManager = placeManager;
        this.dispatcher = dispatcher;
        this.factory = factory;
        this.revealStrategy = revealStrategy;
        this.metaData = propertyMetaData;

        this.providerAdapter = new EntityAdapter<MessagingProvider>(
                MessagingProvider.class,
                propertyMetaData
        );

        this.securityAdapter = new EntityAdapter<SecurityPattern>(
                SecurityPattern.class,
                propertyMetaData
        );

        this.addressingAdapter = new EntityAdapter<AddressingPattern>(
                AddressingPattern.class,
                propertyMetaData
        );
    }

    @Override
    public void prepareFromRequest(PlaceRequest request) {
        currentServer = request.getParameter("name", null);
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }


    @Override
    protected void onReset() {
        super.onReset();
        loadProviderDetails();
        loadSecurityConfig();
        loadAddressingConfig();
        loadJMSConfig();
    }

    private void loadProviderDetails() {

        AddressBinding address = metaData.getBeanMetaData(MessagingProvider.class).getAddress();
        ModelNode operation = address.asResource(Baseadress.get(), getCurrentServer());
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(RECURSIVE).set(Boolean.TRUE);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                MessagingProvider provider = providerAdapter.fromDMR(response.get(RESULT));
                getView().setProviderDetails(provider);

            }
        });
    }

    private void loadSecurityConfig() {

        ModelNode operation = new ModelNode();
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "messaging");
        operation.get(ADDRESS).add("hornetq-server", getCurrentServer());
        operation.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        operation.get(CHILD_TYPE).set("security-setting");
        operation.get(RECURSIVE).set(true);

        final EntityAdapter<SecurityPattern> adapter =
                new EntityAdapter<SecurityPattern>(
                        SecurityPattern.class, metaData
                );

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                List<Property> patterns = response.get(RESULT).asPropertyList();
                List<SecurityPattern> payload = new LinkedList<SecurityPattern>();

                for(Property pattern : patterns)
                {
                    String patternName = pattern.getName();
                    ModelNode patternValue = pattern.getValue().asObject();

                    if(patternValue.hasDefined("role"))
                    {
                        List<Property> roles = patternValue.get("role").asPropertyList();

                        for(Property role : roles)
                        {
                            String roleName = role.getName();
                            ModelNode roleValue = role.getValue().asObject();

                            SecurityPattern securityPattern = adapter.fromDMR(roleValue);
                            securityPattern.setPattern(patternName);
                            securityPattern.setRole(roleName);
                            payload.add(securityPattern);
                        }
                    }

                }

                securitySettings = payload;
                getView().setSecurityConfig(payload);

            }
        });
    }


    private void loadAddressingConfig() {


        ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "messaging");
        operation.get(ADDRESS).add("hornetq-server", getCurrentServer());
        operation.get(CHILD_TYPE).set("address-setting");
        operation.get(RECURSIVE).set(Boolean.TRUE);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());

                List<AddressingPattern> addrPatterns = new ArrayList<AddressingPattern>();
                List<Property> payload = response.get(RESULT).asPropertyList();

                for(Property prop : payload)
                {
                    String pattern = prop.getName();
                    ModelNode value = prop.getValue().asObject();

                    AddressingPattern model = addressingAdapter.fromDMR(value);
                    model.setPattern(pattern);

                    addrPatterns.add(model);

                }


                getView().setAddressingConfig(addrPatterns);

            }
        });
    }

    @Override
    protected void revealInParent() {
        revealStrategy.revealInParent(this);
    }

    public void launchNewSecDialogue() {
        window = new DefaultWindow(Console.MESSAGES.createTitle("Security Setting"));
        window.setWidth(480);
        window.setHeight(360);
        window.addCloseHandler(new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {

            }
        });

        window.setWidget(
                new NewSecurityPatternWizard(this, providerEntity).asWidget()
        );

        window.setGlassEnabled(true);
        window.center();
    }

    public void onCreateSecPattern(final SecurityPattern newEntity) {
        closeDialogue();

        ModelNode composite = new ModelNode();
        composite .get(OP).set(COMPOSITE);
        composite .get(ADDRESS).setEmptyList();
        List<ModelNode> steps = new ArrayList<ModelNode>();

        // the parent resourc, if needed

        boolean parentDoesExist = false;
        for(SecurityPattern setting : securitySettings)
        {
            if(setting.getPattern().equals(newEntity.getPattern()))
            {
                parentDoesExist = true;
                break;
            }
        }

        if(!parentDoesExist)
        {
            // insert a step to create the parent
            ModelNode createParentOp = new ModelNode();
            createParentOp.get(OP).set(ADD);
            createParentOp.get(ADDRESS).set(Baseadress.get());
            createParentOp.get(ADDRESS).add("subsystem", "messaging");
            createParentOp.get(ADDRESS).add("hornetq-server", getCurrentServer());
            createParentOp.get(ADDRESS).add("security-setting", newEntity.getPattern());

            steps.add(createParentOp);
        }

        // the child resource

        AddressBinding address = metaData.getBeanMetaData(SecurityPattern.class).getAddress();
        ModelNode addressModel = address.asResource(
                Baseadress.get(),
                getCurrentServer(),
                newEntity.getPattern(), newEntity.getRole()
        );

        ModelNode createChildOp = securityAdapter.fromEntity(newEntity);
        createChildOp.get(OP).set(ADD);
        createChildOp.get(ADDRESS).set(addressModel.get(ADDRESS).asObject());

        steps.add(createChildOp);

        composite.get(STEPS).set(steps);

        dispatcher.execute(new DMRAction(composite), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.added("security setting"));
                else
                    Console.error(Console.MESSAGES.addingFailed("security setting" + newEntity.getPattern()), response.toString());

                loadSecurityConfig();
            }
        });
    }

    public void onSaveSecDetails(final SecurityPattern pattern, Map<String, Object> changedValues) {

        AddressBinding address = metaData.getBeanMetaData(SecurityPattern.class).getAddress();
        ModelNode proto = address.asResource(Baseadress.get(), getCurrentServer(), pattern.getPattern(), pattern.getRole());
        proto.get(OP).set(WRITE_ATTRIBUTE_OPERATION);

        List<PropertyBinding> bindings = metaData.getBindingsForType(SecurityPattern.class);
        ModelNode operation  = ModelAdapter.detypedFromChangeset(proto, changedValues, bindings);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ResponseWrapper<Boolean> response = ModelAdapter.wrapBooleanResponse(result);
                if(response.getUnderlying())
                    Console.info(Console.MESSAGES.saved("security setting "+pattern.getPattern()));
                else
                    Console.error(Console.MESSAGES.saveFailed("security setting " + pattern.getPattern()), response.getResponse().toString());

                loadSecurityConfig();
            }
        });
    }

    public void onDeleteSecDetails(final SecurityPattern pattern) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(REMOVE);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "messaging");
        operation.get(ADDRESS).add("hornetq-server", getCurrentServer());
        operation.get(ADDRESS).add("security-setting", pattern.getPattern());
        operation.get(ADDRESS).add("role", pattern.getRole());

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.deleted("security setting"));
                else
                    Console.error(Console.MESSAGES.deletionFailed("security setting " + pattern.getPattern()), response.toString());

                loadSecurityConfig();
            }
        });
    }

    public void onDeleteAddressDetails(final AddressingPattern addressingPattern) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(REMOVE);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "messaging");
        operation.get(ADDRESS).add("hornetq-server", getCurrentServer());
        operation.get(ADDRESS).add("address-setting", addressingPattern.getPattern());

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.deleted("address setting"));
                else
                    Console.error(Console.MESSAGES.deletionFailed("address setting " + addressingPattern.getPattern()), response.toString());

                loadAddressingConfig();
            }
        });
    }

    public void launchNewAddrDialogue() {
        window = new DefaultWindow(Console.MESSAGES.createTitle("Addressing Setting"));
        window.setWidth(480);
        window.setHeight(360);
        window.addCloseHandler(new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {

            }
        });

        window.setWidget(
                new NewAddressPatternWizard(this, providerEntity).asWidget()
        );

        window.setGlassEnabled(true);
        window.center();
    }

    public void onSaveAddressDetails(final AddressingPattern entity, Map<String, Object> changedValues) {
        ModelNode proto = new ModelNode();
        proto.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        proto.get(ADDRESS).set(Baseadress.get());
        proto.get(ADDRESS).add("subsystem", "messaging");
        proto.get(ADDRESS).add("hornetq-server", getCurrentServer());
        proto.get(ADDRESS).add("address-setting", entity.getPattern());

        List<PropertyBinding> bindings = metaData.getBindingsForType(AddressingPattern.class);
        ModelNode operation  = ModelAdapter.detypedFromChangeset(proto, changedValues, bindings);

        System.out.println(operation);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ResponseWrapper<Boolean> response = ModelAdapter.wrapBooleanResponse(result);
                if(response.getUnderlying())
                    Console.info(Console.MESSAGES.saved("address setting " + entity.getPattern()));
                else
                    Console.error(Console.MESSAGES.saveFailed("address setting " + entity.getPattern()), response.getResponse().toString());

                loadAddressingConfig();
            }
        });
    }

    public void onCreateAddressPattern(final AddressingPattern address) {
        closeDialogue();

        ModelNode operation = new ModelNode();
        operation.get(OP).set(ADD);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "messaging");
        operation.get(ADDRESS).add("hornetq-server", getCurrentServer());
        operation.get(ADDRESS).add("address-setting", address.getPattern());

        operation.get("dead-letter-address").set(address.getDeadLetterQueue());
        operation.get("expiry-address").set(address.getExpiryQueue());
        operation.get("max-delivery-attempts").set(address.getMaxDelivery());
        operation.get("redelivery-delay").set(address.getRedeliveryDelay());

        System.out.println(operation);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.added("Address Setting"));
                else
                    Console.error(Console.MESSAGES.addingFailed("address setting")+ address.getPattern(), response.toString());

                loadAddressingConfig();
            }
        });
    }


    // JMS
    void loadJMSConfig() {

        ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(RECURSIVE).set(Boolean.TRUE);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "messaging");
        operation.get(ADDRESS).add("hornetq-server", getCurrentServer());

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                ModelNode payload = response.get("result").asObject();

                parseFactories(payload);
                parseQueues(payload);
                parseTopics(payload);
            }
        });

    }

    private void parseQueues(ModelNode response) {

        List<Property> propList = response.get("jms-queue").asPropertyList();
        List<Queue> queues = new ArrayList<Queue>(propList.size());

        for(Property prop : propList)
        {
            Queue queue = factory.queue().as();
            queue.setName(prop.getName());

            ModelNode propValue = prop.getValue();
            String jndi = propValue.get("entries").asList().get(0).asString();
            queue.setJndiName(jndi);

            if(propValue.hasDefined("durable"))
                queue.setDurable(propValue.get("durable").asBoolean());

            if(propValue.hasDefined("selector"))
                queue.setSelector(propValue.get("selector").asString());

            queues.add(queue);
        }

        getJMSView().setQueues(queues);
    }

    private void parseTopics(ModelNode response) {
        List<Property> propList = response.get("jms-topic").asPropertyList();
        List<JMSEndpoint> topics = new ArrayList<JMSEndpoint>(propList.size());

        for(Property prop : propList)
        {
            JMSEndpoint topic = factory.topic().as();
            topic.setName(prop.getName());

            ModelNode propValue = prop.getValue();
            String jndi = propValue.get("entries").asList().get(0).asString();
            topic.setJndiName(jndi);

            topics.add(topic);
        }

        getJMSView().setTopics(topics);

    }

    private void parseFactories(ModelNode response) {
        try {

            // factories
            List<Property> factories = response.get("connection-factory").asPropertyList();
            List<ConnectionFactory> factoryModels = new ArrayList<ConnectionFactory>(factories.size());

            for(Property factoryProp : factories)
            {
                String name = factoryProp.getName();

                ModelNode factoryValue = factoryProp.getValue();
                String jndi = factoryValue.get("entries").asList().get(0).asString();

                ConnectionFactory factoryModel = factory.connectionFactory().as();
                factoryModel.setName(name);
                factoryModel.setJndiName(jndi);

                factoryModels.add(factoryModel);
            }


            getJMSView().setConnectionFactories(factoryModels);

        } catch (Throwable e) {
            Console.error("Failed to parse response: " + e.getMessage());
        }
    }


    public void onEditQueue() {
        getJMSView().enableEditQueue(true);
    }

    public void onSaveQueue(final String name, Map<String, Object> changedValues) {
        getJMSView().enableEditQueue(false);

        if(changedValues.isEmpty()) return;

        ModelNode proto = new ModelNode();
        proto.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        proto.get(ADDRESS).set(Baseadress.get());
        proto.get(ADDRESS).add("subsystem", "messaging");
        proto.get(ADDRESS).add("hornetq-server", getCurrentServer());
        proto.get(ADDRESS).add("jms-queue", name);

        // selector hack
        //if(changedValues.containsKey("selector") && changedValues.get("selector").equals(""))
        //    changedValues.put("selector", "undefined");

        List<PropertyBinding> bindings = metaData.getBindingsForType(Queue.class);
        ModelNode operation  = ModelAdapter.detypedFromChangeset(proto, changedValues, bindings);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.saved("queue "+name));
                else
                    Console.error(Console.MESSAGES.saveFailed("queue " + name), response.toString());

                loadJMSConfig();
            }
        });

    }

    public void onCreateQueue(final Queue entity) {

        closeDialogue();

        ModelNode queue = new ModelNode();
        queue.get(OP).set(ADD);
        queue.get(ADDRESS).set(Baseadress.get());
        queue.get(ADDRESS).add("subsystem", "messaging");
        queue.get(ADDRESS).add("hornetq-server", getCurrentServer());
        queue.get(ADDRESS).add("jms-queue", entity.getName());

        queue.get("entries").setEmptyList();
        queue.get("entries").add(entity.getJndiName());

        queue.get("durable").set(entity.isDurable());

        if(entity.getSelector()!=null && !entity.getSelector().equals(""))
            queue.get("selector").set(entity.getSelector());

        dispatcher.execute(new DMRAction(queue), new AsyncCallback<DMRResponse>() {

            @Override
            public void onFailure(Throwable caught) {
                Console.error("Failed to create queue", caught.getMessage());
            }

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.added("Queue "+entity.getName()));
                else
                    Console.error(Console.MESSAGES.addingFailed("Queue " + entity.getName()), response.toString());

                Console.schedule(new Command() {
                    @Override
                    public void execute() {
                        loadJMSConfig();
                    }
                });

            }
        });

    }

    public void onDeleteQueue(final Queue entity) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(REMOVE);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "messaging");
        operation.get(ADDRESS).add("hornetq-server", getCurrentServer());
        operation.get(ADDRESS).add("jms-queue", entity.getName());

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.deleted("Queue " + entity.getName()));
                else
                    Console.error(Console.MESSAGES.deletionFailed("Queue " + entity.getName()), response.toString());

                loadJMSConfig();

            }
        });
    }

    public void launchNewQueueDialogue() {
        window = new DefaultWindow(Console.MESSAGES.createTitle("JMS Queue"));
        window.setWidth(480);
        window.setHeight(360);
        window.addCloseHandler(new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {

            }
        });

        window.setWidget(
                new NewQueueWizard(this).asWidget()
        );

        window.setGlassEnabled(true);
        window.center();
    }

    public void onDeleteTopic(final JMSEndpoint entity) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(REMOVE);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "messaging");
        operation.get(ADDRESS).add("hornetq-server", getCurrentServer());
        operation.get(ADDRESS).add("jms-topic", entity.getName());

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.deleted("topic" + entity.getName()));
                else
                    Console.error(Console.MESSAGES.deletionFailed("topic " + entity.getName()), response.toString());

                loadJMSConfig();
            }
        });
    }

    public void onEditTopic() {
        getJMSView().enableEditTopic(true);
    }

    public void onSaveTopic(final String name, Map<String, Object> changedValues) {
        getJMSView().enableEditTopic(false);

        if(changedValues.isEmpty()) return;

        ModelNode proto = new ModelNode();
        proto.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        proto.get(ADDRESS).set(Baseadress.get());
        proto.get(ADDRESS).add("subsystem", "messaging");
        proto.get(ADDRESS).add("hornetq-server", getCurrentServer());
        proto.get(ADDRESS).add("jms-topic", name);

        List<PropertyBinding> bindings = metaData.getBindingsForType(Topic.class);
        ModelNode operation  = ModelAdapter.detypedFromChangeset(proto, changedValues, bindings);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.saved("topic "+name));
                else
                    Console.error(Console.MESSAGES.saveFailed("topic " + name), response.toString());

                loadJMSConfig();
            }
        });
    }

    public void launchNewTopicDialogue() {
        window = new DefaultWindow(Console.MESSAGES.createTitle("JMS Topic"));
        window.setWidth(480);
        window.setHeight(360);
        window.addCloseHandler(new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {

            }
        });

        window.setWidget(
                new NewTopicWizard(this).asWidget()
        );

        window.setGlassEnabled(true);
        window.center();
    }

    public void closeDialogue() {
        window.hide();
    }

    public void onCreateTopic(final JMSEndpoint entity) {
        closeDialogue();

        ModelNode topic = new ModelNode();
        topic.get(OP).set(ADD);
        topic.get(ADDRESS).set(Baseadress.get());
        topic.get(ADDRESS).add("subsystem", "messaging");
        topic.get(ADDRESS).add("hornetq-server", getCurrentServer());
        topic.get(ADDRESS).add("jms-topic", entity.getName());

        topic.get("entries").setEmptyList();
        topic.get("entries").add(entity.getJndiName());

        dispatcher.execute(new DMRAction(topic), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = ModelNode.fromBase64(result.getResponseText());
                boolean successful = response.get(OUTCOME).asString().equals(SUCCESS);
                if(successful)
                    Console.info(Console.MESSAGES.added("topic "+entity.getName()));
                else
                    Console.error(Console.MESSAGES.addingFailed("topic " + entity.getName()), response.toString());

                Console.schedule(new Command() {
                    @Override
                    public void execute() {
                        loadJMSConfig();
                    }
                });

            }
        });
    }

    private JMSView getJMSView() {
        return (JMSView)getView();
    }

    public void onSaveProviderConfig(Map<String, Object> changeset) {

        ModelNode proto = new ModelNode();
        proto.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        proto.get(ADDRESS).set(Baseadress.get());
        proto.get(ADDRESS).add("subsystem", "messaging");
        proto.get(ADDRESS).add("hornetq-server", getCurrentServer());

        List<PropertyBinding> bindings = metaData.getBindingsForType(MessagingProvider.class);
        ModelNode operation  = ModelAdapter.detypedFromChangeset(proto, changeset, bindings);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ResponseWrapper<Boolean> response = ModelAdapter.wrapBooleanResponse(result);
                if(response.getUnderlying())
                    Console.info(Console.MESSAGES.saved("provider configuration "+getCurrentServer()));
                else
                    Console.error(Console.MESSAGES.saveFailed("provider configuration " + getCurrentServer()), response.getResponse().toString());

                loadProviderDetails();
            }
        });

    }

    public String getCurrentServer() {

        if(null==currentServer)
            throw new IllegalArgumentException("Current server name not set!");

        return currentServer;
    }
}
