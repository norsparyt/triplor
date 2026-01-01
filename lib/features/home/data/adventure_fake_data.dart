import 'package:triplor/shared/models/date_range_model.dart';
import 'package:triplor/shared/models/location_model.dart';

import '../domain/models/adventure_model.dart';

class FakeAdventureRepository {
  List<Adventure> fakeAdventures = [
    Adventure(
      id: "1",
      userId: "2",
      location: LocationModel(city: "Goa", country: "India"),
      dateRange: DateRangeModel(
        startDate: DateTime.now(),
        endDate: DateTime.now().add(Duration(days: 7)),
      ),
      styles: {AdventureStyle.Backpacking, AdventureStyle.Culture},
      description: "Hi lets goo!",
    ),
  ];
  //GET METHOD
  Future<List<Adventure>> fetchAdventures() async {
    await Future.delayed(const Duration(milliseconds: 500));
    return fakeAdventures;
  }

  //todo check if this is needed as async
  Future<Adventure> fetchAdventureDetail(String id) async {
    await Future.delayed(const Duration(milliseconds: 500));
    final adventure = fakeAdventures.firstWhere(
      (a) => a.id == id,
      orElse: () => throw Exception('Adventure not found'),
    );
    return adventure;
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
    fakeAdventures.insert(0, newAdventure);
    for (Adventure adventure in fakeAdventures) {
      print(adventure.location.displayName);
    }
    return newAdventure;
  }
}
