import '../../../shared/models/location_model.dart';
import '../../../shared/models/date_range_model.dart';
import '../domain/models/adventure_model.dart';

class FakeAdventureRepository{

  final fakeAdventures = [
    Adventure(
      id: 'adv1',
      userId: 'user1',
      location: LocationModel(city: 'Manali', country: 'India'),
      dateRange: DateRangeModel(
        startDate: DateTime(2025, 3, 10),
        endDate: DateTime(2025, 3, 18),
      ),
      type: AdventureType.backpacking,
      description: 'Backpacking across Manali & Kasol. Flexible plans.',
      maxPeople: 3,
    ),
    Adventure(
      id: 'adv2',
      userId: 'user2',
      location: LocationModel(city: 'Goa', country: 'India'),
      dateRange: DateRangeModel(
        startDate: DateTime(2025, 4, 5),
        endDate: DateTime(2025, 4, 12),
      ),
      type: AdventureType.leisure,
      description: 'Beach hopping + cafes. Chill vibe.',
    ),
  ];

  Future<List<Adventure>> fetchAdventures() async {
    await Future.delayed(const Duration(milliseconds: 500));
    return fakeAdventures;
  }
}