import 'package:flutter_test/flutter_test.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';
import 'package:triplor/features/home/providers/create_adventure_form_state.dart';

void main() {
  test(
    'CreateAdventureFormState should not share mutable Set between states',
    () {
      // Initial state
      final state1 = CreateAdventureFormState(
        selectedAdventureStyles: {AdventureStyle.Backpacking},
      );

      // Create a new state via copyWith
      final state2 = state1.copyWith();

      // Mutate the second state's Set
      state2.selectedAdventureStyles.add(AdventureStyle.Hiking);

      // EXPECTATION:
      // state1 should remain unchanged
      expect(
        state1.selectedAdventureStyles.contains(AdventureStyle.Hiking),
        false,
        reason:
            'Mutating state2 should not affect state1 (shared Set detected)',
      );
    },
  );
}
