/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.livespark.formmodeler.editor.client.editor.rendering;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.IsWidget;
import org.gwtbootstrap3.client.shared.event.ModalHideEvent;
import org.gwtbootstrap3.client.shared.event.ModalHideHandler;
import org.gwtbootstrap3.client.ui.Container;
import org.gwtbootstrap3.client.ui.Modal;
import org.livespark.formmodeler.editor.client.editor.FormEditorHelper;
import org.livespark.formmodeler.editor.client.editor.events.FieldDroppedEvent;
import org.livespark.formmodeler.editor.client.editor.events.FieldRemovedEvent;
import org.livespark.formmodeler.editor.client.editor.events.FormContextRequest;
import org.livespark.formmodeler.editor.client.editor.events.FormContextResponse;
import org.livespark.formmodeler.editor.client.editor.rendering.renderers.FieldRenderer;
import org.livespark.formmodeler.editor.model.FieldDefinition;
import org.livespark.formmodeler.editor.model.FormLayoutComponent;
import org.livespark.formmodeler.editor.service.FieldManager;
import org.uberfire.backend.vfs.Path;
import org.uberfire.ext.layout.editor.client.components.*;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by pefernan on 9/22/15.
 */
@Dependent
public class DraggableFieldComponent implements FormLayoutComponent,
        LayoutDragComponent, HasDragAndDropSettings, HasModalConfiguration, HasOnDropNotification, HasOnRemoveNotification {

    public final String[] SETTINGS_KEYS = new String[] { FORM_ID, FIELD_NAME };

    protected Container content = new Container(  );

    protected FieldDefinitionPropertiesModal modal;

    @Inject
    protected FieldRendererManager fieldRendererManager;

    @Inject
    protected Event<FormContextRequest> fieldRequest;

    @Inject
    protected Event<FieldDroppedEvent> fieldDroppedEvent;

    @Inject
    protected Event<FieldRemovedEvent> fieldRemovedEvent;

    protected FormEditorHelper editorHelper;

    protected String formId;

    protected String fieldName;

    protected Path formPath;

    protected FieldDefinition field;

    protected FieldRenderer renderer;

    public void init( String formId, FieldDefinition field, Path formPath) {
        this.formId = formId;
        this.field = field;
        this.formPath = formPath;

        this.fieldName = field.getName();

        findRenderer();
    }

    protected void findRenderer() {
        renderer = fieldRendererManager.getRendererForField( field );
        if ( renderer != null ) {
            renderer.init(this, formPath);
        }
    }

    @Override
    public String[] getSettingsKeys() {
        return SETTINGS_KEYS;
    }

    @Override
    public void setSettingValue( String key, String value ) {
        if ( FORM_ID.equals( key )) formId = value;
        else if (FIELD_NAME.equals( key )) {
            if ( value.startsWith( FieldManager.UNBINDED_FIELD_NAME_PREFFIX ) && value.endsWith( FieldManager.FIELD_NAME_SEPARATOR )) {
                value = value + (new Date()).getTime();
            }
            fieldName = value;
        }
    }

    @Override
    public String getSettingValue( String key ) {
        if ( FORM_ID.equals( key )) return formId;
        else if (FIELD_NAME.equals( key )) return fieldName;
        return null;
    }

    @Override
    public Modal getConfigurationModal( final ModalConfigurationContext ctx ) {

        ctx.getComponentProperties().put(FORM_ID, formId);
        ctx.getComponentProperties().put(FIELD_NAME, fieldName);

        if (field == null) getCurrentField( ctx.getComponentProperties() );

        modal = new FieldDefinitionPropertiesModal( new Command() {
            @Override
            public void execute() {
                modal.hide();

            }
        } );

        modal.addHideHandler( new ModalHideHandler() {
            @Override
            public void onHide(ModalHideEvent evt) {
                ctx.setComponentProperty(FIELD_NAME, fieldName);
                ctx.configurationFinished();
                if ( renderer != null ) renderContent();
                modal = null;
            }
        } );
        if ( renderer != null ) renderer.loadFieldProperties( modal );

        return modal;
    }

    @Override
    public void onDropComponent() {
        fieldDroppedEvent.fire(new FieldDroppedEvent(formId, fieldName));
    }

    @Override
    public void onRemoveComponent() {
        fieldRemovedEvent.fire(new FieldRemovedEvent(formId, fieldName));
    }

    @Override
    public IsWidget getDragWidget() {
        return renderer.getDragWidget();
    }

    @Override
    public IsWidget getPreviewWidget( RenderingContext ctx ) {
        return generateContent(ctx);
    }

    @Override
    public IsWidget getShowWidget( RenderingContext ctx ) {
        return generateContent( ctx );
    }

    protected Container generateContent( RenderingContext ctx ) {
        if (renderer != null) {
            renderContent();
        } else {
            getCurrentField( ctx.getComponent().getProperties() );
        }

        return content;
    }

    protected void renderContent() {
        content.clear();
        content.add( renderer.renderWidget() );
    }

    protected void getCurrentField( Map<String, String> properties ) {
        if (field != null) return;

        if (fieldName == null) {
            fieldName = properties.get( FIELD_NAME );
        }

        if (formId == null) {
            formId = properties.get( FORM_ID );
        }

        fieldRequest.fire(new FormContextRequest(formId, fieldName));
    }

    public void onFieldResponse(@Observes FormContextResponse response) {
        if ( !response.getFormId().equals( formId ) || !response.getFieldName().equals( fieldName ) ) return;
        editorHelper = response.getEditorHelper();
        init(formId, editorHelper.getFormField(fieldName), editorHelper.getContent().getPath());
        if ( renderer != null ) {
            renderContent();
            if ( modal != null ) {
                renderer.loadFieldProperties( modal );
            }
        }
    }

    public List<String> getCompatibleFields() {
        return editorHelper.getCompatibleFields(field);
    }

    public List<String> getCompatibleFieldTypes() {
        return editorHelper.getCompatibleFieldTypes(field);
    }

    public void switchToField(String bindingExpression) {
        if (field.getBindingExpression().equals( bindingExpression )) return;

        FieldDefinition destField = editorHelper.switchToField( field, bindingExpression );

        if ( destField == null ) return;

        fieldDroppedEvent.fire( new FieldDroppedEvent( formId, destField.getName() ) );
        fieldRemovedEvent.fire( new FieldRemovedEvent( formId, field.getName() ) );

        fieldName = destField.getName();
        field = destField;

        renderer.setField( destField );

        renderContent();

        renderer.loadFieldProperties( modal );

    }

    public void switchToFieldType( String typeCode ) {
        if ( field.getCode().equals(typeCode) ) return;

        field = editorHelper.switchToFieldType( field, typeCode);

        findRenderer();

        if ( renderer != null ) renderContent();

    }
}
