class LocationModel {
  final String city;
  final String country;
  final double? lat;
  final double? lng;

  const LocationModel({
    required this.city,
    required this.country,
    this.lat,
    this.lng,
  });

  String get displayName => '$city, $country';
}