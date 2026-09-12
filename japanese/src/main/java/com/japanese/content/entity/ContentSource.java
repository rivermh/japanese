package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_sources")
public class ContentSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_ref", nullable = false, unique = true, length = 160)
    private String sourceRef;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(length = 80)
    private String version;

    @Column(length = 500)
    private String licenseSummary;

    @Column(length = 500)
    private String licenseUrl;

    @Column(length = 2000)
    private String attribution;

    @Column(length = 1000)
    private String usageNote;

    protected ContentSource() {
    }

    public ContentSource(
            String sourceRef,
            String displayName,
            String version,
            String licenseSummary,
            String licenseUrl,
            String attribution,
            String usageNote
    ) {
        this.sourceRef = sourceRef;
        this.displayName = displayName;
        this.version = version;
        this.licenseSummary = licenseSummary;
        this.licenseUrl = licenseUrl;
        this.attribution = attribution;
        this.usageNote = usageNote;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVersion() {
        return version;
    }

    public String getLicenseSummary() {
        return licenseSummary;
    }

    public String getLicenseUrl() {
        return licenseUrl;
    }

    public String getAttribution() {
        return attribution;
    }

    public String getUsageNote() {
        return usageNote;
    }
}
