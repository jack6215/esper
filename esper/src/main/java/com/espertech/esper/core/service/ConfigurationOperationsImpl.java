/**************************************************************************************
 * Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 * http://esper.codehaus.org                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package com.espertech.esper.core.service;

import com.espertech.esper.client.*;
import com.espertech.esper.collection.Pair;
import com.espertech.esper.epl.core.EngineImportException;
import com.espertech.esper.epl.core.EngineImportService;
import com.espertech.esper.epl.core.EngineSettingsService;
import com.espertech.esper.epl.metric.MetricReportingService;
import com.espertech.esper.epl.spec.PluggableObjectCollection;
import com.espertech.esper.epl.variable.VariableExistsException;
import com.espertech.esper.epl.variable.VariableReader;
import com.espertech.esper.epl.variable.VariableService;
import com.espertech.esper.epl.variable.VariableTypeException;
import com.espertech.esper.event.EventAdapterException;
import com.espertech.esper.event.EventAdapterService;
import com.espertech.esper.event.EventTypeIdGenerator;
import com.espertech.esper.event.EventTypeUtility;
import com.espertech.esper.event.vaevent.ValueAddEventProcessor;
import com.espertech.esper.event.vaevent.ValueAddEventService;
import com.espertech.esper.event.vaevent.VariantEventType;
import com.espertech.esper.event.xml.SchemaModel;
import com.espertech.esper.event.xml.XSDSchemaMapper;
import com.espertech.esper.filter.FilterService;
import com.espertech.esper.pattern.pool.PatternSubexpressionPoolEngineSvc;
import com.espertech.esper.util.JavaClassHelper;

import java.io.Serializable;
import java.net.URI;
import java.util.*;

/**
 * Provides runtime engine configuration operations.
 */
public class ConfigurationOperationsImpl implements ConfigurationOperations
{
    private final EventAdapterService eventAdapterService;
    private final EventTypeIdGenerator eventTypeIdGenerator;
    private final EngineImportService engineImportService;
    private final VariableService variableService;
    private final EngineSettingsService engineSettingsService;
    private final ValueAddEventService valueAddEventService;
    private final MetricReportingService metricReportingService;
    private final StatementEventTypeRef statementEventTypeRef;
    private final StatementVariableRef statementVariableRef;
    private final PluggableObjectCollection plugInViews;
    private final FilterService filterService;
    private final PatternSubexpressionPoolEngineSvc patternSubexpressionPoolSvc;

    /**
     * Ctor.
     * @param eventAdapterService is the event wrapper and type service
     * @param engineImportService for imported aggregation functions and static functions
     * @param variableService - provides access to variable values
     * @param engineSettingsService - some engine settings are writable
     * @param valueAddEventService - update event handling
     * @param metricReportingService - for metric reporting
     * @param statementEventTypeRef - statement to event type reference holding 
     * @param statementVariableRef - statement to variable reference holding 
     */
    public ConfigurationOperationsImpl(EventAdapterService eventAdapterService,
                                       EventTypeIdGenerator eventTypeIdGenerator,
                                       EngineImportService engineImportService,
                                       VariableService variableService,
                                       EngineSettingsService engineSettingsService,
                                       ValueAddEventService valueAddEventService,
                                       MetricReportingService metricReportingService,
                                       StatementEventTypeRef statementEventTypeRef,
                                       StatementVariableRef statementVariableRef,
                                       PluggableObjectCollection plugInViews,
                                       FilterService filterService,
                                       PatternSubexpressionPoolEngineSvc patternSubexpressionPoolSvc)
    {
        this.eventAdapterService = eventAdapterService;
        this.eventTypeIdGenerator = eventTypeIdGenerator;
        this.engineImportService = engineImportService;
        this.variableService = variableService;
        this.engineSettingsService = engineSettingsService;
        this.valueAddEventService = valueAddEventService;
        this.metricReportingService = metricReportingService;
        this.statementEventTypeRef = statementEventTypeRef;
        this.statementVariableRef = statementVariableRef;
        this.plugInViews = plugInViews;
        this.filterService = filterService;
        this.patternSubexpressionPoolSvc = patternSubexpressionPoolSvc;
    }

    public void addEventTypeAutoName(String javaPackageName)
    {
        eventAdapterService.addAutoNamePackage(javaPackageName);
    }

    public void addPlugInView(String namespace, String name, String viewFactoryClass)
    {
        ConfigurationPlugInView configurationPlugInView = new ConfigurationPlugInView();
        configurationPlugInView.setNamespace(namespace);
        configurationPlugInView.setName(name);
        configurationPlugInView.setFactoryClassName(viewFactoryClass);
        plugInViews.addViews(Collections.singletonList(configurationPlugInView), Collections.<ConfigurationPlugInVirtualDataWindow>emptyList());
    }

    public void addPlugInAggregationFunction(String functionName, String aggregationClassName)
    {
        try
        {
            ConfigurationPlugInAggregationFunction desc = new ConfigurationPlugInAggregationFunction(functionName, aggregationClassName, null);
            engineImportService.addAggregation(functionName, desc);
        }
        catch (EngineImportException e)
        {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    public void addPlugInAggregationMultiFunction(ConfigurationPlugInAggregationMultiFunction config) throws ConfigurationException {
        try
        {
            engineImportService.addAggregationMultiFunction(config);
        }
        catch (EngineImportException e)
        {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    public void addPlugInAggregationFunctionFactory(String functionName, String aggregationFactoryClassName)
    {
        try
        {
            ConfigurationPlugInAggregationFunction desc = new ConfigurationPlugInAggregationFunction(functionName, null, aggregationFactoryClassName);
            engineImportService.addAggregation(functionName, desc);
        }
        catch (EngineImportException e)
        {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    public void addPlugInSingleRowFunction(String functionName, String className, String methodName) throws ConfigurationException {
        internalAddPlugInSingleRowFunction(functionName, className, methodName, ConfigurationPlugInSingleRowFunction.ValueCache.DISABLED, ConfigurationPlugInSingleRowFunction.FilterOptimizable.ENABLED, false);
    }

    public void addPlugInSingleRowFunction(String functionName, String className, String methodName, ConfigurationPlugInSingleRowFunction.ValueCache valueCache) throws ConfigurationException {
        internalAddPlugInSingleRowFunction(functionName, className, methodName, valueCache, ConfigurationPlugInSingleRowFunction.FilterOptimizable.ENABLED, false);
    }

    public void addPlugInSingleRowFunction(String functionName, String className, String methodName, ConfigurationPlugInSingleRowFunction.FilterOptimizable filterOptimizable) throws ConfigurationException {
        internalAddPlugInSingleRowFunction(functionName, className, methodName, ConfigurationPlugInSingleRowFunction.ValueCache.DISABLED, filterOptimizable, false);
    }

    public void addPlugInSingleRowFunction(String functionName, String className, String methodName, ConfigurationPlugInSingleRowFunction.ValueCache valueCache, ConfigurationPlugInSingleRowFunction.FilterOptimizable filterOptimizable, boolean rethrowExceptions) throws ConfigurationException {
        internalAddPlugInSingleRowFunction(functionName, className, methodName, valueCache, filterOptimizable, rethrowExceptions);
    }

    private void internalAddPlugInSingleRowFunction(String functionName, String className, String methodName, ConfigurationPlugInSingleRowFunction.ValueCache valueCache, ConfigurationPlugInSingleRowFunction.FilterOptimizable filterOptimizable, boolean rethrowExceptions) throws ConfigurationException {
        try
        {
            engineImportService.addSingleRow(functionName, className, methodName, valueCache, filterOptimizable, rethrowExceptions);
        }
        catch (EngineImportException e)
        {
            throw new ConfigurationException(e.getMessage(), e);
        }

    }

    public void addImport(String importName)
    {
        try
        {
            engineImportService.addImport(importName);
        }
        catch (EngineImportException e)
        {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    public void addImport(Class importClass)
    {
        addImport(importClass.getName());
    }

    public boolean isEventTypeExists(String eventTypeName) {
        return eventAdapterService.getExistsTypeByName(eventTypeName) != null;
    }

    public void addEventType(String eventTypeName, String javaEventClassName)
    {
        try
        {
            eventAdapterService.addBeanType(eventTypeName, javaEventClassName, false, false, true, true);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addEventType(String eventTypeName, Class javaEventClass)
    {
        try
        {
            eventAdapterService.addBeanType(eventTypeName, javaEventClass, false, true, true);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addEventType(Class javaEventClass)
    {
        try
        {
            eventAdapterService.addBeanType(javaEventClass.getSimpleName(), javaEventClass, false, true, true);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addEventType(String eventTypeName, Properties typeMap)
    {
        Map<String, Object> types = JavaClassHelper.getClassObjectFromPropertyTypeNames(typeMap);
        try
        {
            eventAdapterService.addNestableMapType(eventTypeName, types, null, false, true, true, false, false);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addEventType(String eventTypeName, Map<String, Object> typeMap)
    {
        try
        {
            Map<String, Object> compiledProperties = EventTypeUtility.compileMapTypeProperties(typeMap, eventAdapterService);
            eventAdapterService.addNestableMapType(eventTypeName, compiledProperties, null, false, true, true, false, false);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addEventType(String eventTypeName, Map<String, Object> typeMap, String[] superTypes) throws ConfigurationException
    {
        ConfigurationEventTypeMap optionalConfig = null;
        if ((superTypes != null) && (superTypes.length > 0))
        {
            optionalConfig = new ConfigurationEventTypeMap();
            optionalConfig.getSuperTypes().addAll(Arrays.asList(superTypes));
        }

        try
        {
            eventAdapterService.addNestableMapType(eventTypeName, typeMap, optionalConfig, false, true, true, false, false);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addEventType(String eventTypeName, Map<String, Object> typeMap, ConfigurationEventTypeMap mapConfig) throws ConfigurationException {
        try
        {
            eventAdapterService.addNestableMapType(eventTypeName, typeMap, mapConfig, false, true, true, false, false);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addEventType(String eventTypeName, String[] propertyNames, Object[] propertyTypes) throws ConfigurationException {
        addEventType(eventTypeName, propertyNames, propertyTypes, null);
    }

    public void addEventType(String eventTypeName, String[] propertyNames, Object[] propertyTypes, ConfigurationEventTypeObjectArray optionalConfiguration) throws ConfigurationException {
        try
        {
            LinkedHashMap<String, Object> propertyTypesMap = EventTypeUtility.validateObjectArrayDef(propertyNames, propertyTypes);
            Map<String, Object> compiledProperties = EventTypeUtility.compileMapTypeProperties(propertyTypesMap, eventAdapterService);
            eventAdapterService.addNestableObjectArrayType(eventTypeName, compiledProperties, optionalConfiguration, false, true, true, false, false);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addEventType(String eventTypeName, ConfigurationEventTypeXMLDOM xmlDOMEventTypeDesc)
    {
        SchemaModel schemaModel = null;

        if ((xmlDOMEventTypeDesc.getSchemaResource() != null) || (xmlDOMEventTypeDesc.getSchemaText() != null))
        {
            try
            {
                schemaModel = XSDSchemaMapper.loadAndMap(xmlDOMEventTypeDesc.getSchemaResource(), xmlDOMEventTypeDesc.getSchemaText(), 2);
            }
            catch (Exception ex)
            {
                throw new ConfigurationException(ex.getMessage(), ex);
            }
        }

        try
        {
            eventAdapterService.addXMLDOMType(eventTypeName, xmlDOMEventTypeDesc, schemaModel, false);
        }
        catch (EventAdapterException t)
        {
            throw new ConfigurationException(t.getMessage(), t);
        }
    }

    public void addVariable(String variableName, Class type, Object initializationValue) throws ConfigurationException
    {
        addVariable(variableName, type.getName(), initializationValue, false);
    }

    public void addVariable(String variableName, String eventTypeName, Object initializationValue) throws ConfigurationException
    {
        addVariable(variableName, eventTypeName, initializationValue, false);
    }

    public void addVariable(String variableName, String type, Object initializationValue, boolean constant) throws ConfigurationException {
        try
        {
            Pair<String, Boolean> arrayType = JavaClassHelper.isGetArrayType(type);
            variableService.createNewVariable(variableName, arrayType.getFirst(), initializationValue, constant, arrayType.getSecond(), null, engineImportService);
            statementVariableRef.addConfiguredVariable(variableName);
        }
        catch (VariableExistsException e)
        {
            throw new ConfigurationException("Error creating variable: " + e.getMessage(), e);
        }
        catch (VariableTypeException e)
        {
            throw new ConfigurationException("Error creating variable: " + e.getMessage(), e);
        }
    }

    public void addPlugInEventType(String eventTypeName, URI[] resolutionURIs, Serializable initializer)
    {
        try
        {
            eventAdapterService.addPlugInEventType(eventTypeName, resolutionURIs, initializer);
        }
        catch (EventAdapterException e)
        {
            throw new ConfigurationException("Error adding plug-in event type: " + e.getMessage(), e);
        }
    }

    public void setPlugInEventTypeResolutionURIs(URI[] urisToResolveName)
    {
        engineSettingsService.setPlugInEventTypeResolutionURIs(urisToResolveName);
    }

    public void addRevisionEventType(String revisioneventTypeName, ConfigurationRevisionEventType revisionEventTypeConfig)
    {
        valueAddEventService.addRevisionEventType(revisioneventTypeName, revisionEventTypeConfig, eventAdapterService);
    }

    public void addVariantStream(String varianteventTypeName, ConfigurationVariantStream variantStreamConfig)
    {
        valueAddEventService.addVariantStream(varianteventTypeName, variantStreamConfig, eventAdapterService, eventTypeIdGenerator);
    }

    public void updateMapEventType(String mapeventTypeName, Map<String, Object> typeMap) throws ConfigurationException
    {
        try
        {
            eventAdapterService.updateMapEventType(mapeventTypeName, typeMap);
        }
        catch (EventAdapterException e)
        {
            throw new ConfigurationException("Error updating Map event type: " + e.getMessage(), e);
        }
    }

    public void updateObjectArrayEventType(String objectArrayEventTypeName, String[] propertyNames, Object[] propertyTypes) throws ConfigurationException
    {
        try
        {
            LinkedHashMap<String, Object> typeMap = EventTypeUtility.validateObjectArrayDef(propertyNames, propertyTypes);
            eventAdapterService.updateObjectArrayEventType(objectArrayEventTypeName, typeMap);
        }
        catch (EventAdapterException e)
        {
            throw new ConfigurationException("Error updating Object-array event type: " + e.getMessage(), e);
        }
    }

    public void replaceXMLEventType(String xmlEventTypeName, ConfigurationEventTypeXMLDOM config) throws ConfigurationException {
        SchemaModel schemaModel = null;
        if (config.getSchemaResource() != null || config.getSchemaText() != null)
        {
            try
            {
                schemaModel = XSDSchemaMapper.loadAndMap(config.getSchemaResource(), config.getSchemaText(), 2);
            }
            catch (Exception ex)
            {
                throw new ConfigurationException(ex.getMessage(), ex);
            }
        }

        try
        {
            eventAdapterService.replaceXMLEventType(xmlEventTypeName, config, schemaModel);
        }
        catch (EventAdapterException e)
        {
            throw new ConfigurationException("Error updating XML event type: " + e.getMessage(), e);
        }
    }

    public void setMetricsReportingInterval(String stmtGroupName, long newInterval)
    {
        try
        {
            metricReportingService.setMetricsReportingInterval(stmtGroupName, newInterval);
        }
        catch (RuntimeException e)
        {
            throw new ConfigurationException("Error updating interval for metric reporting: " + e.getMessage(), e);
        }
    }


    public void setMetricsReportingStmtEnabled(String statementName)
    {
        try
        {
            metricReportingService.setMetricsReportingStmtEnabled(statementName);
        }
        catch (RuntimeException e)
        {
            throw new ConfigurationException("Error enabling metric reporting for statement: " + e.getMessage(), e);
        }
    }

    public void setMetricsReportingStmtDisabled(String statementName)
    {
        try
        {
            metricReportingService.setMetricsReportingStmtDisabled(statementName);
        }
        catch (RuntimeException e)
        {
            throw new ConfigurationException("Error enabling metric reporting for statement: " + e.getMessage(), e);
        }
    }

    public void setMetricsReportingEnabled()
    {
        try
        {
            metricReportingService.setMetricsReportingEnabled();
        }
        catch (RuntimeException e)
        {
            throw new ConfigurationException("Error enabling metric reporting: " + e.getMessage(), e);
        }
    }

    public void setMetricsReportingDisabled()
    {
        try
        {
            metricReportingService.setMetricsReportingDisabled();
        }
        catch (RuntimeException e)
        {
            throw new ConfigurationException("Error enabling metric reporting: " + e.getMessage(), e);
        }
    }

    public boolean isVariantStreamExists(String name)
    {
        ValueAddEventProcessor processor = valueAddEventService.getValueAddProcessor(name);
        if (processor == null)
        {
            return false;
        }
        return processor.getValueAddEventType() instanceof VariantEventType;
    }

    public boolean removeEventType(String name, boolean force) throws ConfigurationException
    {
        if (!force) {
            Set<String> statements = statementEventTypeRef.getStatementNamesForType(name);
            if ((statements != null) && (!statements.isEmpty())) {
                throw new ConfigurationException("Event type '" + name + "' is in use by one or more statements");
            }
        }

        EventType type = eventAdapterService.getExistsTypeByName(name);
        if (type == null)
        {
            return false;
        }

        eventAdapterService.removeType(name);
        statementEventTypeRef.removeReferencesType(name);
        filterService.removeType(type);
        return true;
    }

    public boolean removeVariable(String name, boolean force) throws ConfigurationException
    {
        if (!force) {
            Set<String> statements = statementVariableRef.getStatementNamesForVar(name);
            if ((statements != null) && (!statements.isEmpty())) {
                throw new ConfigurationException("Variable '" + name + "' is in use by one or more statements");
            }
        }

        VariableReader reader = variableService.getReader(name);
        if (reader == null)
        {
            return false;
        }

        variableService.removeVariable(name);
        statementVariableRef.removeReferencesVariable(name);
        statementVariableRef.removeConfiguredVariable(name);
        return true;
    }

    public Set<String> getEventTypeNameUsedBy(String name)
    {
        Set<String> statements = statementEventTypeRef.getStatementNamesForType(name);
        if ((statements == null) || (statements.isEmpty()))
        {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(statements);
    }

    public Set<String> getVariableNameUsedBy(String variableName)
    {
        Set<String> statements = statementVariableRef.getStatementNamesForVar(variableName);
        if ((statements == null) || (statements.isEmpty()))
        {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(statements);
    }

    public EventType getEventType(String eventTypeName) {
        return eventAdapterService.getExistsTypeByName(eventTypeName);
    }

    public EventType[] getEventTypes() {
        return eventAdapterService.getAllTypes();
    }

    public void addEventType(String eventTypeName, String eventClass, ConfigurationEventTypeLegacy legacyEventTypeDesc)
    {
        try {
            Map<String, ConfigurationEventTypeLegacy> map = new HashMap<String, ConfigurationEventTypeLegacy>();
            map.put(eventClass, legacyEventTypeDesc);
            eventAdapterService.setClassLegacyConfigs(map);
            eventAdapterService.addBeanType(eventTypeName, eventClass, false, false, false, true);
        }
        catch (EventAdapterException ex) {
            throw new ConfigurationException("Failed to add legacy event type definition for type '" + eventTypeName + "': " + ex.getMessage(), ex);
        }
    }

    public void setPatternMaxSubexpressions(Long maxSubexpressions) {
        patternSubexpressionPoolSvc.setPatternMaxSubexpressions(maxSubexpressions);
    }
}
