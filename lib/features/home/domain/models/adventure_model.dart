import '../../../../shared/models/date_range_model.dart';
import '../../../../shared/models/location_model.dart';

enum AdventureStyle {
  Adventure(label: 'Adventure'),
  Backpacking(label: 'Backpacking'),
  Relaxing(label: 'Relaxing'),
  Foodie(label: 'Foodie'),
  Culture(label: 'Culture'),
  Hiking(label: 'Hiking');

  final String label;
  const AdventureStyle({required this.label});
}

class Adventure {
  final String id;

  /// User who created this adventure
  final String userId;

  final LocationModel location;
  final DateRangeModel dateRange;
  final Set<AdventureStyle> styles;
  //style= way of adventure ie the enum above
  //whereas type= either solo or group adventure
  final String description;

  /// Optional but useful for feed
  final int? maxPeople;
  final bool isOpen;
  //todo use case

  const Adventure({
    required this.id,
    required this.userId,
    required this.location,
    required this.dateRange,
    required this.styles,
    required this.description,
    this.maxPeople,
    this.isOpen = true,
  });
}
