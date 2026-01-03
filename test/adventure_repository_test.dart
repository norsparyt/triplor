import 'package:flutter_test/flutter_test.dart';
import 'package:triplor/features/home/data/adventure_repository.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';
import 'package:triplor/shared/models/date_range_model.dart';
import 'package:triplor/shared/models/location_model.dart';

void main() {
  late AdventureRepository repository;

  setUp(() {
    repository = AdventureRepository();
  });

  test('getAdventureById returns cached adventure', () async {
    // Act
    final firstFetch = repository.getAdventureById('1');
    final secondFetch = repository.getAdventureById('1');

    // Assert
    expect(firstFetch, isNotNull);
    expect(identical(firstFetch, secondFetch), true);
  });

  test('createAdventure adds adventure to cache', () async {
    final newAdventure = Adventure(
      id: 'temp',
      userId: 'user-1',
      location: LocationModel(city: 'Manali', country: 'India'),
      dateRange: DateRangeModel(
        startDate: DateTime(2026, 1, 10),
        endDate: DateTime(2026, 1, 15),
      ),
      styles: {},
      description: 'Test adventure',
      maxPeople: 4,
    );

    // Act
    final created = await repository.createAdventure(newAdventure);
    final fetched = repository.getAdventureById(created.id);

    // Assert
    expect(fetched.id, created.id);
    expect(fetched.location.city, 'Manali');
  });

  test('fetchAdventures returns cached list', () async {
    final list1 = await repository.fetchAdventures();
    final list2 = await repository.fetchAdventures();

    expect(list1.length, list2.length);
    expect(identical(list1, list2), true);
  });

  test('getAdventureById throws when adventure not found', () {
    expect(() => repository.getAdventureById('invalid-id'), throwsException);
  });
}
