package com.triplor.adventure.place;

/** A place as the API shows it, both in search results and embedded in an adventure. */
public record PlaceResponse(long id, String name, String admin1, String countryCode, double lat, double lng) {

    public static PlaceResponse of(Place place) {
        return new PlaceResponse(
                place.getId(), place.getName(), place.getAdmin1(), place.getCountryCode(),
                place.getLat(), place.getLng());
    }
}
