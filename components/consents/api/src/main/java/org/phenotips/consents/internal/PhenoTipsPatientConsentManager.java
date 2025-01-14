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
package org.phenotips.consents.internal;

import org.phenotips.Constants;
import org.phenotips.consents.Consent;
import org.phenotips.consents.ConsentManager;
import org.phenotips.consents.ConsentStatus;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.translation.TranslationManager;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.uiextension.UIExtension;
import org.xwiki.uiextension.UIExtensionManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * {@link ConsentManager} that integrates with XWiki and the {@link DefaultConsent}.
 *
 * @version $Id$
 * @since 1.3M1
 */
@Component
@Singleton
public class PhenoTipsPatientConsentManager implements ConsentManager, Initializable
{
    private static final String GRANTED = "granted";

    private static final String RENDERING_MODE = "view";

    private static final String FIELDS = "fields";

    /**
     * Logging helper object.
     */
    @Inject
    private Logger logger;

    /**
     * Provides access to the XWiki data.
     */
    @Inject
    private DocumentAccessBridge bridge;

    @Inject
    private PatientRepository repository;

    /**
     * Fills in missing reference fields with those from the current context document to create a full reference.
     */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<EntityReference> referenceResolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private TranslationManager translationManager;

    @Inject
    private QueryManager qm;

    /**
     * Lists the patient form fields.
     */
    @Inject
    private UIExtensionManager uixManager;

    private EntityReference consentReference =
        new EntityReference("PatientConsentConfiguration", EntityType.DOCUMENT, Constants.CODE_SPACE_REFERENCE);

    private EntityReference consentIdsHolderReference =
        new EntityReference("PatientConsent", EntityType.DOCUMENT, Constants.CODE_SPACE_REFERENCE);

    private EntityReference configurationPageReference =
        new EntityReference("Consents", EntityType.DOCUMENT, Constants.CODE_SPACE_REFERENCE);

    private Date lastSystemConsentLoadTime;

    private Set<Consent> cachedSystemConsents = Collections.unmodifiableSet(new LinkedHashSet<Consent>());

    @Override
    public void initialize() throws InitializationException
    {
    }

    @Override
    public Set<Consent> getSystemConsents()
    {
        try {
            DocumentReference configDocRef = this.referenceResolver.resolve(this.configurationPageReference);
            XWikiDocument configDoc = (XWikiDocument) this.bridge.getDocument(configDocRef);
            if (this.lastSystemConsentLoadTime == null || configDoc.getDate().after(this.lastSystemConsentLoadTime)) {
                updateSystemConsentCache(configDoc);
            }
        } catch (Exception ex) {
            this.logger.error("Could not load preferences document: {}", ex.getMessage());
        }
        return this.cachedSystemConsents;
    }

    private synchronized void updateSystemConsentCache(XWikiDocument configDoc)
    {
        try {
            Set<Consent> consents = new LinkedHashSet<>();
            List<BaseObject> consentObjects = configDoc.getXObjects(this.consentReference);
            if (consentObjects != null) {
                for (BaseObject consentObject : consentObjects) {
                    Consent nextConsent = fromXWikiConsentConfiguration(consentObject, configDoc);
                    if (nextConsent != null) {
                        consents.add(nextConsent);
                    }
                }
            }
            this.cachedSystemConsents = Collections.unmodifiableSet(consents);
            this.lastSystemConsentLoadTime = configDoc.getDate();
        } catch (Exception ex) {
            this.logger.error("Could not load system consents from preferences document: {}", ex.getMessage());
        }
    }

    // suppressing conversion of List returned by getListValue() to List<String>
    @SuppressWarnings("unchecked")
    private Consent fromXWikiConsentConfiguration(BaseObject xwikiConsent, XWikiDocument configDoc)
    {
        try {
            String id = xwikiConsent.getStringValue("id");
            String label = cleanDescription(
                configDoc.display("label", RENDERING_MODE, xwikiConsent, this.contextProvider.get()), true);
            if (label == null || label.length() == 0) {
                label = id + " " + this.translationManager.translate("phenotips.consents.label.empty");
            }
            String description = cleanDescription(
                configDoc.display("description", RENDERING_MODE, xwikiConsent, this.contextProvider.get()), false);
            boolean required = intToBool(xwikiConsent.getIntValue("required"));
            boolean affectsFields = intToBool(xwikiConsent.getIntValue("affectsFields"));
            List<String> formFields = null;
            List<String> dataFields = null;
            if (affectsFields) {
                dataFields = new LinkedList<>();
                formFields = xwikiConsent.getListValue(FIELDS);
                fetchConsentFields(formFields, dataFields);
            }
            return new DefaultConsent(id, label, description, required, dataFields, formFields);
        } catch (Exception ex) {
            this.logger.error("A patient consent is improperly configured: {}", ex.getMessage());
        }
        return null;
    }

    private void fetchConsentFields(List<String> formFields, List<String> dataFields) throws QueryException
    {
        // Data fields are found by finding the extension point based off the uix names from the form fields.
        for (String uixName : formFields) {
            Query query = this.qm.createQuery("select distinct uix.extensionPointId from Document doc,"
                + " doc.object(XWiki.UIExtensionClass) as uix where uix.name = :uixname", Query.XWQL);
            query.bindValue("uixname", uixName);
            List<String> results = query.execute();
            if (results.size() != 1) {
                this.logger.warn("There are {} extensions identified by {}", results.size(), uixName);
                if (results.size() == 0) {
                    continue;
                }
            }
            String extensionPointId = results.get(0);
            // Get the Id from the extension point
            List<UIExtension> extensionObjects = this.uixManager.get(extensionPointId);
            for (UIExtension uix : extensionObjects) {
                Map<String, String> parameters = uix.getParameters();
                // Finds the correct UIExtension from the name and extension point
                if (uixName.equals(uix.getId()) && !"false".equals(parameters.get("enabled"))
                    && parameters.containsKey(FIELDS)) {
                    // Separate the fields and add in each string to dataFields
                    dataFields.addAll(Arrays.asList(parameters.get(FIELDS).split("\\s*,\\s*")));
                }
            }
        }
    }

    private static String cleanDescription(String toClean, boolean stripParagraphTags)
    {
        String clean = toClean;
        if (clean != null) {
            if (stripParagraphTags) {
                clean = clean.replace("<div>", "").replace("</div>", "");
                clean = clean.replace("<p>", "").replace("</p>", "");
            }
            clean = clean.replaceAll("[{]{2}(/{0,1})html(.*?)[}]{2}", "");
        }
        return clean;
    }

    @Override
    public boolean isValidConsentId(String consentId)
    {
        Set<Consent> systemConsents = getSystemConsents();
        for (Consent consent : systemConsents) {
            if (StringUtils.equals(consentId, consent.getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<Consent> getMissingConsentsForPatient(String patientId)
    {
        return this.getMissingConsentsForPatient(this.repository.get(patientId));
    }

    @Override
    public Set<Consent> getMissingConsentsForPatient(Patient patient)
    {
        return this.getConsentsForPatient(patient, false);
    }

    @Override
    public Set<Consent> getAllConsentsForPatient(String patientId)
    {
        return this.getAllConsentsForPatient(this.repository.get(patientId));
    }

    @Override
    public Set<Consent> getAllConsentsForPatient(Patient patient)
    {
        return this.getConsentsForPatient(patient, true);
    }

    private Set<Consent> getConsentsForPatient(Patient patient, boolean includeGranted)
    {
        if (patient == null) {
            return null;
        }

        // List of consent ids a patient has agreed to, read from the database
        Set<String> xwikiPatientConsents = readConsentIdsFromPatientDoc(patient);

        Set<Consent> returnedConsents = new LinkedHashSet<>();

        // Using system consents to ignore consents set for the patient but no longer configured in the system
        // (it is faster to check contains() in a set, so iterating through the list and checking the set)
        Set<Consent> systemConsents = getSystemConsents();
        for (Consent systemConsent : systemConsents) {
            if (xwikiPatientConsents.contains(systemConsent.getId())) {
                if (includeGranted) {
                    Consent copy = systemConsent.copy(ConsentStatus.YES);
                    returnedConsents.add(copy);
                }
            } else {
                Consent copy = systemConsent.copy(ConsentStatus.NO);
                returnedConsents.add(copy);
            }
        }

        return returnedConsents;
    }

    @SuppressWarnings("unchecked")
    private Set<String> readConsentIdsFromPatientDoc(Patient patient)
    {
        Set<String> ids = new LinkedHashSet<>();
        try {
            XWikiDocument patientDoc = patient.getXDocument();
            BaseObject idsHolder = patientDoc.getXObject(this.consentIdsHolderReference);
            if (idsHolder != null) {
                List<String> patientConsentIds = idsHolder.getListValue(GRANTED);
                if (patientConsentIds != null) {
                    ids.addAll(patientConsentIds);
                }
            }
        } catch (Exception ex) {
            this.logger.error("Could not read consents for patient {}: {}", patient.getId(), ex.getMessage());
        }
        return ids;
    }

    @Override
    public boolean setPatientConsents(Patient patient, Iterable<String> consents)
    {
        try {
            List<Consent> existingConsents = this.selectFromSystem(consents);
            SaveablePatientConsentHolder holder = this.getPatientConsentHolder(patient);
            holder.setConsents(convertToIds(existingConsents));
            holder.save();
            return true;
        } catch (Exception ex) {
            this.logger.error("Could not update consents in patient record {}. {}", patient, ex.getMessage());
        }
        return false;
    }

    @Override
    public boolean hasConsent(String patientId, String consentId)
    {
        return this.hasConsent(this.repository.get(patientId), consentId);
    }

    @Override
    public boolean hasConsent(Patient patient, String consentId)
    {
        if (patient == null || !isValidConsentId(consentId)) {
            return false;
        }
        Set<Consent> missingPatientConsents = getMissingConsentsForPatient(patient);
        if (missingPatientConsents == null) {
            return false;
        }
        for (Consent consent : missingPatientConsents) {
            if (consent.getId().equals(consentId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return consents that exist in the system and correspond to the given ids
     */
    private List<Consent> selectFromSystem(Iterable<String> ids)
    {
        List<Consent> existingConsents = new LinkedList<>();
        for (String id : ids) {
            for (Consent consent : this.getSystemConsents()) {
                if (StringUtils.equals(consent.getId(), id)) {
                    existingConsents.add(consent);
                    break;
                }
            }
        }
        return existingConsents;
    }

    @Override
    public boolean grantConsent(Patient patient, String consentId)
    {
        return this.manageConsent(patient, consentId, true);
    }

    @Override
    public boolean revokeConsent(Patient patient, String consentId)
    {
        return this.manageConsent(patient, consentId, false);
    }

    /**
     * @param grant if true will grant the consent, otherwise will revoke
     * @return if operation was successful
     */
    private boolean manageConsent(Patient patient, String consentId, boolean grant)
    {
        if (!this.isValidConsentId(consentId)) {
            this.logger.error("Invalid consent id ({}) was supplied", consentId);
            return false;
        }
        try {
            SaveablePatientConsentHolder consentHolder = this.getPatientConsentHolder(patient);
            List<String> currentConsents = consentHolder.getConsents();
            if (grant) {
                if (!currentConsents.contains(consentId)) {
                    currentConsents.add(consentId);
                }
            } else {
                currentConsents.remove(consentId);
            }
            consentHolder.setConsents(currentConsents);
            consentHolder.save();
            return true;
        } catch (Exception ex) {
            this.logger.error("Could not update consent {} in patient record {}. {}",
                consentId, patient.getId(), ex.getMessage());
            return false;
        }
    }

    private SaveablePatientConsentHolder getPatientConsentHolder(Patient patient) throws Exception
    {
        XWikiDocument patientDoc = patient.getXDocument();
        return new SaveablePatientConsentHolder(getXWikiConsentHolder(patientDoc), patientDoc,
            this.contextProvider.get());
    }

    /**
     * Either gets the existing consents holder object, or creates a new one.
     */
    private BaseObject getXWikiConsentHolder(XWikiDocument doc) throws XWikiException
    {
        BaseObject holder = doc.getXObject(this.consentIdsHolderReference);
        if (holder == null) {
            holder = doc.newXObject(this.consentIdsHolderReference, this.contextProvider.get());
        }
        return holder;
    }

    private static List<String> convertToIds(List<Consent> consents)
    {
        List<String> ids = new LinkedList<>();
        for (Consent consent : consents) {
            ids.add(consent.getId());
        }
        return ids;
    }

    private boolean intToBool(int value)
    {
        return value == 1;
    }

    /**
     * Keeps the XWiki patient document and the XWiki consents holder object in memory, allowing to change consents
     * granted and save the document, without reloading either the document or the consent {@link BaseObject}.
     */
    private class SaveablePatientConsentHolder
    {
        private BaseObject consentHolder;

        private XWikiDocument patientDoc;

        private XWikiContext context;

        SaveablePatientConsentHolder(BaseObject consentHolder, XWikiDocument patientDoc, XWikiContext context)
        {
            this.consentHolder = consentHolder;
            this.patientDoc = patientDoc;
            this.context = context;
        }

        @SuppressWarnings("unchecked")
        public List<String> getConsents() throws XWikiException
        {
            return this.consentHolder.getListValue(GRANTED);
        }

        public void setConsents(List<String> consents)
        {
            this.consentHolder.set(GRANTED, consents, this.context);
        }

        public void save() throws XWikiException
        {
            this.context.getWiki().saveDocument(this.patientDoc, "Changed patient consents", true, this.context);
        }
    }

    @Override
    public JSONArray toJSON(Collection<Consent> consents)
    {
        if (consents == null) {
            return null;
        }

        JSONArray result = new JSONArray();
        for (Consent consent : consents) {
            result.put(consent.toJSON());
        }
        return result;
    }

    @Override
    public Set<Consent> fromJSON(JSONArray consentsJSON)
    {
        if (consentsJSON == null) {
            return null;
        }
        Set<Consent> result = new LinkedHashSet<>();
        for (int i = 0; i < consentsJSON.length(); i++) {
            JSONObject consentJSON = consentsJSON.optJSONObject(i);
            if (consentJSON != null) {
                result.add(new DefaultConsent(consentJSON));
            }
        }
        return result;
    }
}
