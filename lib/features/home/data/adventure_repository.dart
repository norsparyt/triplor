import 'package:triplor/shared/models/date_range_model.dart';
import 'package:triplor/shared/models/location_model.dart';

import '../domain/models/adventure_model.dart';

class AdventureRepository {
  final List<Adventure> _cache = [];

  FakeAdventureRepository() {
    _cache.addAll([
      Adventure(
        id: "1",
        userId: "2",
        location: LocationModel(city: "Goa", country: "India"),
        dateRange: DateRangeModel(
          startDate: DateTime.now(),
          endDate: DateTime.now().add(const Duration(days: 7)),
        ),
        styles: {AdventureStyle.Backpacking, AdventureStyle.Culture},
        description:
            "Hi lets goo! Hi lets goo! Hi lets goo! Hi lets goo! Hi lets goo!Hi lets goo! Hi lets goo! Hi lets goo!Hi lets goo!",
        maxPeople: 5,
      ),
    ]);
  }

  // Simulates network once, returns cached list
  Future<List<Adventure>> fetchAdventures() async {
    if (_cache.isNotEmpty) {
      return _cache;
    }

    await Future.delayed(const Duration(milliseconds: 500));
    return _cache;
  }

  /// Cached read → SHOULD NOT be async
  Adventure getAdventureById(String id) {
    return _cache.firstWhere(
      (a) => a.id == id,
      orElse: () => throw Exception('Adventure not found'),
    );
  }

  //POST Method
  Future<Adventure> createAdventure(Adventure adventure) async {
    await Future.delayed(Duration(seconds: 1));

    // For now, return the adventure with a generated ID
    Adventure newAdventure = Adventure(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      userId: adventure.userId,
      location: adventure.location,
      dateRange: adventure.dateRange,
      styles: adventure.styles,
      description: adventure.description,
      maxPeople: adventure.maxPeople,
    );
    _cache.insert(0, newAdventure);
    return newAdventure;
  }
}
