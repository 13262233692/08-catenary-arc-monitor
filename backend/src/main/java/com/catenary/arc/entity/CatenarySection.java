package com.catenary.arc.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatenarySection {

    private String sectionId;

    private String sectionName;

    private String lineName;

    private double startKm;

    private double endKm;

    private String bureauId;

    private String stationId;

    private String workAreaId;

    private String status;
}
