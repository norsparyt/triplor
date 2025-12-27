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

  void toggleTripStyle(AdventureType style) {
    final newStyles = Set<AdventureType>.from(state.selectedTripStyles);
    if (newStyles.contains(style)) {
      newStyles.remove(style);
    } else {
      newStyles.add(style);
    }
    state = state.copyWith(selectedTripStyles: newStyles);
  }

  void toggleTripType(bool isGroupTrip) {
    state = state.copyWith(isGroupTrip: isGroupTrip);
  }

  void incrementMaxPeople() {
    if (state.maxPeople < 20) {
      state = state.copyWith(maxPeople: state.maxPeople + 1);
    }
  }

  void decrementMaxPeople() {
    if (state.maxPeople > 1) {
      state = state.copyWith(maxPeople: state.maxPeople - 1);
    }
  }

  void updateDescription(String description) {
    state = state.copyWith(description: description);
  }

  void reset() {
    state = CreateAdventureFormState();
  }
}
