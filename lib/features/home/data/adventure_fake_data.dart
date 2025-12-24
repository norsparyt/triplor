import '../domain/models/adventure_model.dart';

class FakeAdventureRepository {
  List<Adventure> fakeAdventures = [
    // Adventure(
    //   id: 'adv1',
    //   userId: 'user1',
    //   location: LocationModel(city: 'Manali', country: 'India'),
    //   dateRange: DateRangeModel(
    //     startDate: DateTime(2025, 3, 10),
    //     endDate: DateTime(2025, 3, 18),
    //   ),
    //   type: AdventureType.backpacking,
    //   description: 'Backpacking across Manali & Kasol. Flexible plans.',
    //   maxPeople: 3,
    // ),
    // Adventure(
    //   id: 'adv2',
    //   userId: 'user2',
    //   location: LocationModel(city: 'Goa', country: 'India'),
    //   dateRange: DateRangeModel(
    //     startDate: DateTime(2025, 4, 5),
    //     endDate: DateTime(2025, 4, 12),
    //   ),
    //   type: AdventureType.leisure,
    //   description: 'Beach hopping + cafes. Chill vibe.',
    // ),
  ];
  //GET METHOD
  Future<List<Adventure>> fetchAdventures() async {
    await Future.delayed(const Duration(milliseconds: 500));
    return fakeAdventures;
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
      type: adventure.type,
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
