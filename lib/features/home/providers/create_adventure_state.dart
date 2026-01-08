import '../domain/models/adventure_model.dart';

//Create Adventure State Class- to handle states of creation
// Handles only creation side-effects (API, loading, error)
// Form state is managed separately
class CreateAdventureState {
  final bool isLoading;
  final Adventure? lastSavedAdventure;
  final String? error;

  CreateAdventureState({
    this.isLoading = false,
    this.lastSavedAdventure,
    this.error,
  });

  CreateAdventureState copyWith({
    bool? isLoading,
    Adventure? lastSavedAdventure,
    String? error,
  }) {
    return CreateAdventureState(
      isLoading: isLoading ?? this.isLoading,
      lastSavedAdventure: lastSavedAdventure ?? this.lastSavedAdventure,
      error: error ?? this.error,
    );
  }
}
