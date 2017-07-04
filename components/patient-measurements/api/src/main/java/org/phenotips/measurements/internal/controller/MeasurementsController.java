/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.measurements.internal.controller;

import org.phenotips.data.IndexedPatientData;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.data.PatientDataController;
import org.phenotips.measurements.MeasurementHandler;
import org.phenotips.measurements.data.MeasurementEntry;
import org.phenotips.measurements.internal.MeasurementUtils;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Handles the patient's measurements.
 *
 * @version $Id$
 * @since 1.3M4
 */
@Component(roles = { PatientDataController.class })
@Named("measurements")
@Singleton
public class MeasurementsController implements PatientDataController<MeasurementEntry>
{
    private static final String XCLASS = "PhenoTips.MeasurementClass";

    private static final String DATE = "date";

    private static final String AGE = "age";

    private static final String TYPE = "type";

    private static final String SIDE = "side";

    private static final String VALUE = "value";

    private static final String UNIT = "unit";

    private static final String SD = "sd";

    private static final String PERCENTILE = "percentile";

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private static final String ARMSPAN = "armspan";

    @Inject
    private Logger logger;

    /**
     * Parses string representations of document references into proper references.
     */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> stringResolver;

    @Inject
    @Named("context")
    private Provider<ComponentManager> cm;

    @Inject
    private Provider<XWikiContext> xcontext;

    @Override
    public PatientData<MeasurementEntry> load(Patient patient)
    {
        try {
            XWikiDocument doc = patient.getXDocument();
            List<BaseObject> objects = doc.getXObjects(getXClassReference());
            if (objects == null || objects.isEmpty()) {
                return null;
            }

            List<MeasurementHandler> handlers = getHandlers();
            List<MeasurementEntry> result = new LinkedList<>();

            for (BaseObject object : objects) {
                if (object == null) {
                    continue;
                }
                try {
                    final String age = object.getStringValue(AGE);
                    final Date date = object.getDateValue(DATE);
                    final String type = object.getStringValue(TYPE);
                    final String side = object.getStringValue(SIDE);
                    final Double value = object.getDoubleValue(VALUE);

                    if (date == null && StringUtils.isEmpty(age)) {
                        throw new Exception("Age or date is missing");
                    }
                    if (StringUtils.isEmpty(type)) {
                        throw new Exception("Type is missing");
                    }
                    // getting measurement units, could be null, but should not be
                    MeasurementHandler handler = getHandler(type, handlers);
                    final String units = handler.getUnit();

                    MeasurementEntry entry = new MeasurementEntry(date, age, type, side, value, units);
                    result.add(entry);
                } catch (Exception e) {
                    this.logger.error("Failed to load a particular measurement", e.getMessage());
                }
            }

            return new IndexedPatientData<>(getName(), result);
        } catch (Exception e) {
            this.logger.error("Could not find requested document or some unforeseen"
                + " error has occurred during measurements controller loading ", e.getMessage());
        }
        return null;
    }

    private EntityReference getXClassReference()
    {
        return this.stringResolver.resolve(XCLASS);
    }

    private List<MeasurementHandler> getHandlers()
    {
        try {
            return this.cm.get().getInstanceList(MeasurementHandler.class);
        } catch (ComponentLookupException e) {
            this.logger.error("Failed to find component", e);
        }
        return Collections.emptyList();
    }

    private MeasurementHandler getHandler(String name, List<MeasurementHandler> handlers)
    {
        // would be better if a lambda was returned in getHandlers()
        for (MeasurementHandler handler : handlers) {
            if (StringUtils.equals(handler.getName(), name)) {
                return handler;
            }
        }
        return null;
    }

    @Override
    public void save(Patient patient)
    {
        try {
            XWikiDocument doc = patient.getXDocument();
            XWikiContext context = this.xcontext.get();

            PatientData<MeasurementEntry> entries = patient.getData(getName());
            if (!entries.isIndexed()) {
                return;
            }
            doc.removeXObjects(this.getXClassReference());
            Iterator<MeasurementEntry> iterator = entries.iterator();
            while (iterator.hasNext()) {
                MeasurementEntry entry = iterator.next();
                BaseObject wikiObject = doc.newXObject(this.getXClassReference(), context);
                wikiObject.set(DATE, entry.getDate(), context);
                wikiObject.set(AGE, entry.getAge(), context);
                wikiObject.set(TYPE, entry.getType(), context);
                wikiObject.set(SIDE, entry.getSide(), context);
                wikiObject.set(VALUE, entry.getValue(), context);
            }
        } catch (Exception e) {
            this.logger.error("Failed to save measurements: [{}]", e.getMessage());
        }
    }

    @Override
    public void writeJSON(Patient patient, JSONObject json)
    {
        this.writeJSON(patient, json, null);
    }

    @Override
    public void writeJSON(Patient patient, JSONObject json, Collection<String> selectedFieldNames)
    {
        if (selectedFieldNames == null || selectedFieldNames.contains(getName())) {
            PatientData<MeasurementEntry> data = patient.getData(getName());
            if (data == null) {
                return;
            }

            JSONArray result = new JSONArray();
            Iterator<MeasurementEntry> iterator = data.iterator();
            List<MeasurementHandler> handlers = getHandlers();
            while (iterator.hasNext()) {
                MeasurementEntry entry = iterator.next();
                JSONObject jsonEntry = entryToJson(entry);
                Map<String, Object> dependantInfo = computeDependantInfo(entry, patient, handlers);
                for (String item : dependantInfo.keySet()) {
                    jsonEntry.put(item, dependantInfo.get(item));
                }
                result.put(jsonEntry);
            }

            if (!(result.length() == 0)) {
                json.put(getName(), result);
            }
        }
    }

    private Map<String, Object> computeDependantInfo(MeasurementEntry entry, Patient patient,
        List<MeasurementHandler> handlers)
    {
        // not adding these constants into the class definition yet
        String maleConst = "M";
        String femaleConst = "F";
        // an age string and type must be present
        if (StringUtils.isBlank(entry.getAge()) || StringUtils.isBlank(entry.getType()) || entry.getValue() == null) {
            return new HashMap<>();
        }
        Double ageInMonths = MeasurementUtils.convertAgeStrToNumMonths(entry.getAge());

        // getting gender for computations
        PatientData<String> sexData = patient.getData("sex");
        String sex = (sexData != null) ? sexData.getValue() : "";
        // ideally, the constants from the SexController should be used here,
        // but that would introduce a hard dependency. Maybe add it to constants?
        if (!entry.getType().equals(ARMSPAN)
            && (StringUtils.equals(sex, maleConst) || StringUtils.equals(sex, femaleConst))) {
            // has to be either male or female, otherwise how can we compute SD?
            // finding the handler
            MeasurementHandler handler = getHandler(entry.getType(), handlers);
            boolean isMale = StringUtils.equals(maleConst, sex);
            double sd = handler.valueToStandardDeviation(isMale, ageInMonths.floatValue(), entry.getValue());
            Integer percentile = handler.valueToPercentile(isMale, ageInMonths.floatValue(), entry.getValue());

            Map<String, Object> result = new HashMap<>();
            result.put(SD, (Double.isInfinite(sd) || Double.isNaN(sd)) ? "" : sd);
            result.put(PERCENTILE, percentile);
            return result;
        }
        return new HashMap<>();
    }

    private JSONObject entryToJson(MeasurementEntry entry)
    {
        JSONObject json = new JSONObject();
        if (entry.getDate() != null) {
            // not efficient to create a new one for every entry
            SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
            json.put(DATE, formatter.format(entry.getDate()));
        }
        json.put(AGE, entry.getAge());
        json.put(TYPE, entry.getType());
        if (StringUtils.isNotBlank(entry.getSide())) {
            json.put(SIDE, entry.getSide());
        }
        json.put(VALUE, entry.getValue());
        json.put(UNIT, entry.getUnits());
        return json;
    }

    @Override
    public PatientData<MeasurementEntry> readJSON(JSONObject json)
    {
        try {
            if (json == null || json.opt(getName()) == null) {
                return null;
            }
            // if not array, will err
            JSONArray entries = json.optJSONArray(getName());
            List<MeasurementEntry> measurements = new LinkedList<>();
            if (entries.length() == 0) {
                return null;
            }

            for (int i = 0; i < entries.length(); ++i) {
                try {
                    JSONObject entry = entries.optJSONObject(i);
                    MeasurementEntry measurement = jsonToEntry(entry);
                    if (measurement != null && !isDuplicate(measurement, measurements)) {
                        measurements.add(jsonToEntry(entry));
                    }
                } catch (Exception er) {
                    this.logger.error("Could not read a particular JSON block", er.getMessage());
                }
            }

            if (measurements.isEmpty()) {
                return null;
            }
            return new IndexedPatientData<>(getName(), measurements);
        } catch (Exception e) {
            this.logger.error("Could not read JSON", e.getMessage());
        }
        return null;
    }

    private MeasurementEntry jsonToEntry(JSONObject json) throws ParseException
    {
        final JSONObject j = json;
        // not efficient to create a new one for every entry
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
        final Date date = j.optString(DATE, null) != null ? formatter.parse(j.optString(DATE)) : null;
        String type = j.optString(TYPE, null);
        Double value = j.optDouble(VALUE);
        if (date != null && type != null && !value.isNaN()) {
            return new MeasurementEntry(date, j.optString(AGE), type, j.optString(SIDE), value, j.optString(UNIT));
        }
        return null;
    }

    private boolean isDuplicate(MeasurementEntry measurement, List<MeasurementEntry> measurements)
    {
        if (measurements.size() > 0) {
            for (MeasurementEntry item : measurements) {
                if (item.getDate().equals(measurement.getDate()) && item.getType().equals(measurement.getType())
                    && item.getSide().equals(measurement.getSide())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getName()
    {
        return "measurements";
    }
}
