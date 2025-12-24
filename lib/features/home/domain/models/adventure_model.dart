import '../../../../shared/models/date_range_model.dart';
import '../../../../shared/models/location_model.dart';

enum AdventureType {
  backpacking,
  leisure,
  workation,
  trekking,
}

class Adventure {
  final String id;

  /// User who created this adventure
  final String userId;

  final LocationModel location;
  final DateRangeModel dateRange;
  final AdventureType type;
  final String description;

  /// Optional but useful for feed
  final int? maxPeople;
  final bool isOpen;

  const Adventure({
    required this.id,
    required this.userId,
    required this.location,
    required this.dateRange,
    required this.type,
    required this.description,
    this.maxPeople,
    this.isOpen = true,
  });
}