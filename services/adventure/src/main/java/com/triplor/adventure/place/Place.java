package com.triplor.adventure.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * A city from the GeoNames data set. Reference data: loaded by a script, never written by the
 * application, hence {@code @Immutable}.
 */
@Entity
@Immutable
@Table(name = "places")
public class Place {

    /** The GeoNames id. */
    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "ascii_name", nullable = false)
    private String asciiName;

    @Column
    private String admin1;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false)
    private long population;

    protected Place() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAsciiName() {
        return asciiName;
    }

    public String getAdmin1() {
        return admin1;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public long getPopulation() {
        return population;
    }
}
