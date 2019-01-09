package pm.pride;

import static pm.pride.AttributeDescriptor.REVISIONINGFLAG;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class RevisionedRecordDescriptor extends RecordDescriptor {

    public static final String FLAG_IS_REVISIONED = "FLAG_IS_REVISIONED";
    public static final String FLAG_IS_NOT_REVISIONED = "FLAG_IS_NOT_REVISIONED";

    public static final String COLUMN_REVISION_TIMESTAMP = "REVISION_TIMESTAMP";

    private final String revisionTableName;
    private RecordDescriptor recordDescriptorForRevisioning;

    public RevisionedRecordDescriptor(Class objectType, String dbContext, String dbtable, String dbtableAlias, String revisionTableName, RecordDescriptor baseDescriptor, String[][] attributeMap, int extractionMode) throws IllegalDescriptorException {
        super(objectType, dbContext, dbtable, baseDescriptor, attributeMap, extractionMode);
        this.revisionTableName = revisionTableName;
    }

    public RevisionedRecordDescriptor(Class objectType, String dbtable, String revisionTableName, RecordDescriptor baseDescriptor, String[][] attributeMap) throws IllegalDescriptorException {
        super(objectType, dbtable, baseDescriptor, attributeMap);
        this.revisionTableName = revisionTableName;
    }

    public RevisionedRecordDescriptor(Class objectType, String dbtable, String dbtableAlias, String revisionTableName, RecordDescriptor baseDescriptor, String[][] attributeMap, int extractionMode) throws IllegalDescriptorException {
        super(objectType, dbtable, dbtableAlias, baseDescriptor, attributeMap, extractionMode);
        this.revisionTableName = revisionTableName;
    }

    public RevisionedRecordDescriptor(Class objectType, String dbContext, String dbtable, String revisionTableName, RecordDescriptor baseDescriptor, String[][] attributeMap) throws IllegalDescriptorException {
        super(objectType, dbContext, dbtable, null, baseDescriptor, attributeMap);
        this.revisionTableName = revisionTableName;
    }

    public RevisionedRecordDescriptor(RecordDescriptor red, String alias, String altTable, String revisionTableName) {
        super(red, alias, altTable);
        this.revisionTableName = revisionTableName;
    }

    public RevisionedRecordDescriptor(RecordDescriptor red, String alias, String revisionTableName) {
        super(red, alias);
        this.revisionTableName = revisionTableName;
    }

    public RecordDescriptor getRevisioningRecordDescriptor() {
        if (recordDescriptorForRevisioning == null) {
            this.recordDescriptorForRevisioning = new RecordDescriptor(objectType, dbContext, getRevisionTableName(), null, null, buildRevisioningAttributeMap());
        }
        return this.recordDescriptorForRevisioning;
    }

    protected String[][] buildRevisioningAttributeMap() {
        List<String[]> filteredAttributes = extractRawAttributeMapForRevisioning();
        filteredAttributes.add(new String[]{ COLUMN_REVISION_TIMESTAMP, constantValue("systimestamp"), null, FLAG_IS_REVISIONED, Timestamp.class.getName()});
        return filteredAttributes.toArray(new String[0][]);
    }

    public List<String[]> extractRawAttributeMapForRevisioning() {
        List<String[]> filteredAttributeMap = new ArrayList<>();
        for (String[] attribute : getRawAttributeMap()) {
            if (revisioningEnabled(attribute)) {
                filteredAttributeMap.add(attribute);
            }
        }
        return filteredAttributeMap;
    }

    /**
     * By default all attributes of a {@link RevisionedRecordDescriptor} are revisioned.
     * @return true if the attribute should be revisioned
     */
    private boolean revisioningEnabled(String[] attribute) {
        return attribute.length < REVISIONINGFLAG ||
                !FLAG_IS_NOT_REVISIONED.equals(attribute[REVISIONINGFLAG]);
    }

    public String getRevisionTableName() {
        return revisionTableName;
    }

    @Override
    public boolean isRevisioned() {
        return true;
    }

}
