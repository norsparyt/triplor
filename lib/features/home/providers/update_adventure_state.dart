import '../domain/models/adventure_model.dart';

//Update Adventure State Class- to handle states of updation
// Handles only updation side-effects (API, loading, error)
// Form state is managed separately
class UpdateAdventureState {
  final bool isLoading;
  final Adventure? updatedAdventure;
  final String? error;

  UpdateAdventureState({
    this.isLoading = false,
    this.updatedAdventure,
    this.error,
  });

  UpdateAdventureState copyWith({
    bool? isLoading,
    Adventure? updatedAdventure,
    String? error,
  }) {
    return UpdateAdventureState(
      isLoading: isLoading ?? this.isLoading,
      updatedAdventure: updatedAdventure ?? this.updatedAdventure,
      error: error ?? this.error,
    );
  }
}
