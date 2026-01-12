import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'adventure_providers.dart';
import 'delete_adventure_state.dart';

class DeleteAdventureNotifier extends Notifier<DeleteAdventureState> {
  @override
  DeleteAdventureState build() {
    return DeleteAdventureState();
  }

  Future<void> deleteAdventure(String adventureId) async {
    state = state.copyWith(isLoading: true, error: null);

    try {
      final repository = ref.read(adventureRepositoryProvider);
      await repository.deleteById(adventureId);

      // Invalidate the list to trigger refresh
      ref.invalidate(allAdventuresProvider);

      state = state.copyWith(isLoading: false, isDeleted: true);
    } catch (error) {
      state = state.copyWith(isLoading: false, error: error.toString());
    }
  }

  void reset() {
    state = DeleteAdventureState();
  }
}
