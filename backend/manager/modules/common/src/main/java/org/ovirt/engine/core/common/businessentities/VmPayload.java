package org.ovirt.engine.core.common.businessentities;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.utils.VmDeviceType;


public class VmPayload implements Serializable {
    private static final long serialVersionUID = -3665087594884425768L;
    private static final String SpecParamsPayload = "vmPayload";
    private static final String SpecParamsVolumeIdType = "volId";
    private static final String SpecParamsFileType = "file";

    private VmDeviceType type;
    private String volumeId;
    private HashMap<String, String> files; // file data is base64-encoded

    public VmPayload() {
        this.type = VmDeviceType.CDROM;
        this.volumeId = null;
        this.files = new HashMap<String, String>();
    }

    @SuppressWarnings("unchecked")
    public VmPayload(VmDeviceType type, Map<String, Object> specParams) {
        this.type = type;

        Map<String, Object> payload = (Map<String, Object>)specParams.get(SpecParamsPayload);
        this.volumeId = (String)payload.get(SpecParamsVolumeIdType);
        this.files = (HashMap<String, String>)payload.get(SpecParamsFileType);
    }

    public static boolean isPayload(Map<String, Object> specParams) {
        return specParams == null ? false : specParams.containsKey(SpecParamsPayload);
    }

    public static boolean isPayloadSizeLegal(String payload) {
        return payload.length() <= Config.<Integer> GetValue(ConfigValues.PayloadSize);
    }

    public VmDeviceType getType() {
        return this.type;
    }

    public void setType(VmDeviceType type) {
        this.type = type;
    }

    public String getVolumeId() {
        return this.volumeId;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    /**
     * Retrieve a map of files in this payload.  The map is always initialized,
     * and can be updated to add/remove files to/from the payload.
     * The key is the file path, and the value is base64-encoded file content.
     *
     * @return Map of files in this payload
     */
    public HashMap<String, String> getFiles() {
        return files;
    }

    public Map<String, Object> getSpecParams() {
        // function produce something like that:
        // vmPayload={volumeId:volume-id,file:{filename:content,filename2:content2,...}}
        Map<String, Object> specParams = new HashMap<String, Object>();
        Map<String, Object> payload = new HashMap<String, Object>();

        specParams.put(SpecParamsPayload, payload);
        if (volumeId != null) {
            payload.put(SpecParamsVolumeIdType, volumeId);
        }
        payload.put(SpecParamsFileType, files);

        return specParams;
    }
}
