import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';

import '../../../shared/models/date_range_model.dart';
import '../../../shared/models/location_model.dart';
import '../providers/adventure_providers.dart';

class CreateAdventureScreen extends ConsumerStatefulWidget {
  @override
  ConsumerState<ConsumerStatefulWidget> createState() =>
      _CreateAdventureScreen();
}

class _CreateAdventureScreen extends ConsumerState<CreateAdventureScreen> {
  AdventureType _selectedType = AdventureType.backpacking;

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(createAdventureProvider);
    return Scaffold(
      appBar: AppBar(title: Text('Create Adventure')),
      body: Center(
        child: Column(
          children: [
            DropdownButton<AdventureType>(
              value: _selectedType,
              items: AdventureType.values.map((type) {
                return DropdownMenuItem(
                  value: type,
                  child: Text(type.toString().split('.').last),
                );
              }).toList(),
              onChanged: (value) {
                setState(() => _selectedType = value!);
              },
            ),

            SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => handleCreate(),
              child: state.isLoading
                  ? CircularProgressIndicator(color: Colors.white)
                  : Text('Create Adventure'),
            ),
          ],
        ),
      ),
    );
  }

  void handleCreate() async {
    //get the notifier of the provider (to use the model class's methods etc)
    final notifier = ref.read(createAdventureProvider.notifier);
    await notifier.createAdventure(
      userId: 'user1', // Get from auth provider in real app
      location: LocationModel(city: 'Manali', country: 'India'),
      dateRange: DateRangeModel(
        startDate: DateTime(2025, 3, 10),
        endDate: DateTime(2025, 3, 18),
      ),
      type: _selectedType,
      description: 'Fake description',
      maxPeople: 5,
    );

    //get the state of the provider
    final state = ref.read(createAdventureProvider);
    if (state.createdAdventure != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Adventure created successfully!')),
      );
      context.go('/home');
    } else if (state.error != null && mounted) {
      // Error occurred
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Error: ${state.error}')));
    }

    //NOTE both are read because we dont need to watch any changes.
  }
}
