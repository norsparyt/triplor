import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:triplor/features/home/providers/update_adventure_state.dart';

import '../data/adventure_repository_exceptions.dart';
import '../domain/models/adventure_model.dart';
import 'adventure_providers.dart';

class UpdateAdventureNotifier extends Notifier<UpdateAdventureState> {
  @override
  UpdateAdventureState build() {
    return UpdateAdventureState();
  }

  Future<void> updateAdventure(Adventure adventure) async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      final repository = ref.read(adventureRepositoryProvider);

      final updatedAdventure = await repository.updateAdventure(
        adventure: adventure,
      );
      //fetched the updated adventure
      state = state.copyWith(
        isLoading: false,
        updatedAdventure: updatedAdventure,
      );
      ref.invalidate(allAdventuresProvider);
      ref.invalidate(adventureDetailProvider(adventure.id));
    } on AdventureNotFoundException {
      state = state.copyWith(
        isLoading: false,
        error: 'Adventure no longer exists',
      );
    } catch (e) {
      state = state.copyWith(isLoading: false, error: 'Something went wrong');
    }
    // catch (error) {
    //   state = state.copyWith(isLoading: false, error: error.toString());
    // }
  }
}
