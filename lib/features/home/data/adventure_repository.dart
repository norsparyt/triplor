import '../../../shared/models/date_range_model.dart';
import '../../../shared/models/location_model.dart';
import '../domain/models/adventure_model.dart';
import 'adventure_repository_exceptions.dart';

class AdventureRepository {
  final List<Adventure> _cache = [];

  //INIT
  AdventureRepository() {
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

  //READ
  // Simulates network once, returns cached list
  Future<List<Adventure>> fetchAdventures() async {
    //todo redundant since constructor seed
    // if (_cache.isNotEmpty) {
    //   return _cache;
    // }

    await Future.delayed(const Duration(milliseconds: 500));
    _cache.forEach((adventure) => print(adventure.toString()));
    return _cache;
  }

  //READ
  /// TODO Cached read → SHOULD NOT be async?
  Future<Adventure> getAdventureById(String id) async {
    await Future.delayed(const Duration(seconds: 1));
    return _cache.firstWhere(
      (a) => a.id == id,
      orElse: () => throw Exception('Adventure not found'),
    );
  }

  // CREATE
  //POST Method
  Future<Adventure> createAdventure({required Adventure adventure}) async {
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
    _cache.forEach((adventure) => print(adventure.toString()));
    return newAdventure;
  }

  // UPDATE
  Future<Adventure> updateAdventure({required Adventure adventure}) async {
    await Future.delayed(const Duration(seconds: 2));

    final index = _cache.indexWhere((a) => a.id == adventure.id);
    if (index == -1) {
      throw AdventureNotFoundException(adventure.id);
    }

    final existing = _cache[index];

    final updatedAdventure = Adventure(
      id: existing.id,
      userId: existing.userId,
      location: adventure.location,
      dateRange: adventure.dateRange,
      styles: adventure.styles,
      description: adventure.description,
      maxPeople: adventure.maxPeople,
    );

    _cache[index] = updatedAdventure;
    _cache.forEach((adventure) => print(adventure.toString()));
    return updatedAdventure;
  }

  //DELETE
  Future<void> deleteById(String id) async {
    await Future.delayed(const Duration(seconds: 1));
    _cache.removeWhere((a) => a.id == id);
  }
}
