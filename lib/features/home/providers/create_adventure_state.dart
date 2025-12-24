import '../domain/models/adventure_model.dart';

//Create Adventure State Class- to handle states of creation
class CreateAdventureState {
  final bool isLoading;
  final Adventure? createdAdventure;
  final String? error;

  CreateAdventureState({
    this.isLoading = false,
    this.createdAdventure,
    this.error,
  });

  CreateAdventureState copyWith({
    bool? isLoading,
    Adventure? createdAdventure,
    String? error,
  }) {
    return CreateAdventureState(
      isLoading: isLoading ?? this.isLoading,
      createdAdventure: createdAdventure ?? this.createdAdventure,
      error: error ?? this.error,
    );
  }
}
