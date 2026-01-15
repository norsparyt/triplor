import 'package:flutter_test/flutter_test.dart';
import 'package:triplor/features/home/data/adventure_repository.dart';
import 'package:triplor/features/home/data/adventure_repository_exceptions.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';
import 'package:triplor/shared/models/date_range_model.dart';
import 'package:triplor/shared/models/location_model.dart';

void main() {
  late AdventureRepository repository;

  setUp(() {
    repository = AdventureRepository();
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
    final created = await repository.createAdventure(adventure: newAdventure);
    final fetched = await repository.getAdventureById(created.id);

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
    expect(
      () => repository.getAdventureById('invalid-id'),
      throwsA(isA<AdventureNotFoundException>()),
    );
  });

  test('updateAdventure updates existing adventure in cache', () async {
    // Arrange: create adventure first
    final original = Adventure(
      id: 'update-1',
      userId: 'user-1',
      location: LocationModel(city: 'Manali', country: 'India'),
      dateRange: DateRangeModel(
        startDate: DateTime(2026, 1, 10),
        endDate: DateTime(2026, 1, 15),
      ),
      styles: {},
      description: 'Original description',
      maxPeople: 4,
    );

    final created = await repository.createAdventure(adventure: original);

    // Act: update same adventure
    final updated = Adventure(
      id: created.id, // same ID is critical
      userId: 'user-1',
      location: LocationModel(city: 'Kasol', country: 'India'),
      dateRange: original.dateRange,
      styles: {},
      description: 'Updated description',
      maxPeople: 6,
    );

    await repository.updateAdventure(adventure: updated);
    final fetched = await repository.getAdventureById(created.id);

    // Assert
    expect(fetched.id, created.id);
    expect(fetched.location.city, 'Kasol');
    expect(fetched.description, 'Updated description');
    expect(fetched.maxPeople, 6);
  });
  test('updateAdventure throws when adventure does not exist', () async {
    final adventure = Adventure(
      id: 'missing-id',
      userId: 'user-1',
      location: LocationModel(city: 'Delhi', country: 'India'),
      dateRange: DateRangeModel(
        startDate: DateTime(2026, 1, 1),
        endDate: DateTime(2026, 1, 2),
      ),
      styles: {},
      description: 'Should fail',
      maxPeople: 2,
    );

    expect(
      () => repository.updateAdventure(adventure: adventure),
      throwsA(isA<AdventureNotFoundException>()),
    );
  });
}
