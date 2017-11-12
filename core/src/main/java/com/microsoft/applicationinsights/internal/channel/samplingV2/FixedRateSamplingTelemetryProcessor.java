package com.microsoft.applicationinsights.internal.channel.samplingV2;

import com.microsoft.applicationinsights.agent.internal.common.StringUtils;
import com.microsoft.applicationinsights.extensibility.TelemetryProcessor;
import com.microsoft.applicationinsights.internal.annotation.BuiltInProcessor;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.telemetry.SupportSampling;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import org.omg.CORBA.INTERNAL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This processor is used to Perform Sampling on User specified sampling rate
 * <p>
 * How to use in ApplicationInsights Configuration :
 * <p>
 * <BuiltInProcessors>
 * <Processor type = "FixedRateSamplingTelemetryProcessor">
 * <Add name = "SamplingPercentage" value = "50" />
 * <Add name = "ExcludedTypes" value="Trace" />
 * <Add name = "IncludedTypes" value="Trace;Request" />
 * </Processor>
 * </BuiltInProcessors>
 */
@BuiltInProcessor("FixedRateSamplingTelemetryProcessor")
public final class FixedRateSamplingTelemetryProcessor implements TelemetryProcessor {

    private final String dependencyTelemetryName = "Dependency";
    private static final String eventTelemetryName = "Event";
    private static final String exceptionTelemetryName = "Exception";
    private static final String pageViewTelemetryName = "PageView";
    private static final String requestTelemetryName = "Request";
    private static final String traceTelemetryName = "Trace";

    private static final String listSeparator = ";";
    private static Map<String, Class> allowedTypes;

    private Set<Class> excludedTypes;

    private Set<Class> includedTypes;


    /// All sampling percentage must be in a ratio of 100/N where N is a whole number (2, 3, 4, …). E.g. 50 for 1/2 or 33.33 for 1/3.
    /// Failure to follow this pattern can result in unexpected / incorrect computation of values in the portal.
    private double samplingPercentage;

    /**
     * constructor is responsible of initializing this processor
     * to default settings
     */
    public FixedRateSamplingTelemetryProcessor() {
        this.samplingPercentage = 100.00;
        this.includedTypes = new HashSet<Class>();
        this.excludedTypes = new HashSet<Class>();
        try {
            this.allowedTypes = new HashMap<String, Class>() {{
                put(dependencyTelemetryName, Class.forName("com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry"));
                put(eventTelemetryName, Class.forName("com.microsoft.applicationinsights.telemetry.EventTelemetry"));
                put(exceptionTelemetryName, Class.forName("com.microsoft.applicationinsights.telemetry.ExceptionTelemetry"));
                put(pageViewTelemetryName, Class.forName("com.microsoft.applicationinsights.telemetry.PageViewTelemetry"));
                put(requestTelemetryName, Class.forName("com.microsoft.applicationinsights.telemetry.RequestTelemetry"));
                put(traceTelemetryName, Class.forName("com.microsoft.applicationinsights.telemetry.TraceTelemetry"));
            }};
        } catch (ClassNotFoundException e) {
            InternalLogger.INSTANCE.trace("Unable to locate telemetry classes");
        }
    }

    /**
     * This method returns a set of classes of excluded types specified by user
     *
     * @return
     */
    public Set<Class> getExcludedTypes() {
        return excludedTypes;
    }

    /**
     * This method returns a set of classes of included types specified by user
     *
     * @return
     */
    public Set<Class> getIncludedTypes() {
        return includedTypes;
    }


    private void setIncludedOrExcludedTypes(String value, Set<Class> typeSet) {

        if (!StringUtils.isNullOrEmpty(value)) {
            value = value.trim();
            if (!StringUtils.isNullOrEmpty(value) && allowedTypes.containsKey(value)) {
                typeSet.add(allowedTypes.get(value));
            } else {
                InternalLogger.INSTANCE.error("Item is either not allowed to sample or is empty");
            }
        } else {
            InternalLogger.INSTANCE.error("Empty types cannot be considered");
        }
    }

    /**
     * Gets the sample rate currently set
     */
    double getSamplingPercentage() {
        return samplingPercentage;
    }

    /**
     * Sets the user defined sampling percentage
     *
     * @param samplingPercentage
     */
    public void setSamplingPercentage(String samplingPercentage) {
        this.samplingPercentage = Double.valueOf(samplingPercentage);
    }

    /**
     * This method determines if the telemetry needs to be sampled or not.
     *
     * @param telemetry
     * @return
     */
    @Override
    public boolean process(Telemetry telemetry) {

        double samplingPercentage = this.samplingPercentage;

        if (telemetry instanceof SupportSampling) {

            if (isSamplingApplicable(telemetry.getClass())) {

                SupportSampling samplingSupportingTelemetry = ((SupportSampling) telemetry);

                if (samplingSupportingTelemetry.getSamplingPercentage() == null) {

                    samplingSupportingTelemetry.setSamplingPercentage(samplingPercentage);

                    if (SamplingScoreGeneratorV2.getSamplingScore(telemetry) >= samplingPercentage) {

                        InternalLogger.INSTANCE.info("Item %s sampled out", telemetry.getClass());
                        return false;
                    }
                } else {
                    InternalLogger.INSTANCE.info("Item has sampling percentage already set to :"
                            + samplingSupportingTelemetry.getSamplingPercentage());
                }
            } else {
                InternalLogger.INSTANCE.trace("Skip sampling since %s type is not sampling applicable", telemetry.getClass());
            }
        }

        return true;
    }

    /**
     * Determines if the argument is applicable for sampling
     *
     * @param item : Denotes the class item to be determined applicable for sampling
     * @return boolean
     */
    private boolean isSamplingApplicable(Class item) {

        if (excludedTypes.size() > 0 && excludedTypes.contains(item)) {
            return false;
        }

        if (includedTypes.size() > 0 && !includedTypes.contains(item)) {
            return false;
        }

        return true;
    }

    /**
     * This method is invoked during configuration to add one element to the
     * excluded types set from the xml array list of excluded types
     * @param value
     */
    public void addToExcludedType(String value) {

        setIncludedOrExcludedTypes(value, excludedTypes);
        InternalLogger.INSTANCE.trace(value + " added as excluded to sampling");

    }

    /**
     * This method is invoked during configuration to add one element to the
     * included types set from the xml array list of included types
     * @param value
     */
    public void addToIncludedType(String value) {

        setIncludedOrExcludedTypes(value, includedTypes);
        InternalLogger.INSTANCE.trace(value + " added as included to sampling");

    }
}
