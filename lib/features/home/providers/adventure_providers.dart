import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:triplor/features/home/data/adventure_fake_data.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';

final adventureRepositoryProvider= Provider<FakeAdventureRepository>((ref){
    return FakeAdventureRepository();
});

final adventureProvider= FutureProvider<List<Adventure>>((ref) async {
  final repository=ref.watch(adventureRepositoryProvider);
   return await repository.fetchAdventures();
});