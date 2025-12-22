

import '../../../../shared/models/date_range_model.dart';
import '../../../../shared/models/location_model.dart';

enum AdventureType {
  backpacking,
  leisure,
  workation,
  trekking,
}

class AdventureModel {
  final String id;
  final LocationModel location;
  final DateRangeModel dateRange;
  final AdventureType type;
  final String description;

  const AdventureModel({
    required this.id,
    required this.location,
    required this.dateRange,
    required this.type,
    required this.description,
  });
}