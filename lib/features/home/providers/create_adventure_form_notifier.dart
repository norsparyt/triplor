import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';
import 'package:triplor/features/home/providers/create_adventure_form_state.dart';

class CreateAdventureFormNotifier extends Notifier<CreateAdventureFormState> {
  @override
  CreateAdventureFormState build() {
    return CreateAdventureFormState();
  }

  void updateLocation(String location) {
    state = state.copyWith(location: location);
  }

  void updateDates(DateTime startDate, DateTime endDate) {
    state = state.copyWith(startDate: startDate, endDate: endDate);
  }

  void toggleAdventureStyle(AdventureStyle style) {
    final newStyles = Set<AdventureStyle>.from(state.selectedAdventureStyles);
    if (newStyles.contains(style)) {
      newStyles.remove(style);
    } else {
      newStyles.add(style);
    }
    state = state.copyWith(selectedAdventureStyles: newStyles);
  }
  // style= hiking, relxed etc

  void toggleAdventureType(bool isGroupAdventure) {
    state = state.copyWith(isGroupAdventure: isGroupAdventure);
  }
  //type = solo or group

  void incrementMaxPeople() {
    if (state.maxPeople < 20) {
      state = state.copyWith(maxPeople: state.maxPeople + 1);
    }
  }

  void decrementMaxPeople() {
    if (state.maxPeople > 3) {
      state = state.copyWith(maxPeople: state.maxPeople - 1);
    }
  }

  void updateDescription(String description) {
    state = state.copyWith(description: description);
  }

  // void reset() {
  //   state = CreateAdventureFormState();
  // }
}
