import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:triplor/features/home/data/adventure_fake_data.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';

import 'create_adventure_form_notifier.dart';
import 'create_adventure_form_state.dart';
import 'create_adventure_notifier.dart';
import 'create_adventure_state.dart';

final adventureRepositoryProvider = Provider<FakeAdventureRepository>((ref) {
  return FakeAdventureRepository();
});

final allAdventuresProvider = FutureProvider<List<Adventure>>((ref) async {
  final repository = ref.watch(adventureRepositoryProvider);
  return await repository.fetchAdventures();
});

final adventureDetailProvider = FutureProvider.family<Adventure, String>((
  ref,
  id,
) async {
  final repository = ref.read(adventureRepositoryProvider);
  return await repository.fetchAdventureDetail(id);
});

final createAdventureProvider =
    NotifierProvider<CreateAdventureNotifier, CreateAdventureState>(
      CreateAdventureNotifier.new,
    );

final createAdventureFormProvider =
    NotifierProvider<CreateAdventureFormNotifier, CreateAdventureFormState>(
      CreateAdventureFormNotifier.new,
      isAutoDispose: true,
    );
