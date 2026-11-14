import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';
import 'package:triplor/features/home/presentation/create_adventure_screen.dart';
import 'package:triplor/features/home/providers/adventure_providers.dart';
import 'package:triplor/shared/models/date_range_model.dart';
import 'package:triplor/shared/models/location_model.dart';

void main() {
  testWidgets(
    'Edit screen populates form on first open when entered directly (regression)',
    (tester) async {
      final adventure = Adventure(
        id: '1',
        userId: 'u1',
        location: LocationModel(city: 'Goa', country: 'India'),
        dateRange: DateRangeModel(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 1, 10),
        ),
        styles: {AdventureStyle.Culture},
        description: 'Original description',
        maxPeople: 3,
      );

      final container = ProviderContainer(
        overrides: [
          adventureDetailProvider.overrideWith((ref, id) async {
            // Simulate backend delay
            await Future.delayed(const Duration(milliseconds: 500));
            return adventure;
          }),
        ],
      );

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            home: CreateAdventureScreen(adventureId: '1'),
          ),
        ),
      );

      // First frame (provider still loading)
      await tester.pump();

      // Provider resolves
      await tester.pump(const Duration(milliseconds: 500));

      // Post-frame callback runs
      await tester.pump();

      // ✅ ASSERT: form populated on FIRST open
      expect(find.text('Goa'), findsOneWidget);
      expect(find.text('Original description'), findsOneWidget);
      expect(find.text('3'), findsOneWidget);
    },
  );
}
