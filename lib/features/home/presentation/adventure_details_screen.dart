import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/constants/app_strings.dart';
import '../providers/adventure_providers.dart';
import 'adventure_details_view.dart';

class AdventureDetailsScreen extends ConsumerStatefulWidget {
  final String adventureId;

  const AdventureDetailsScreen({super.key, required this.adventureId});

  @override
  ConsumerState<AdventureDetailsScreen> createState() =>
      _AdventureDetailsScreenState();
}

class _AdventureDetailsScreenState
    extends ConsumerState<AdventureDetailsScreen> {
  @override
  Widget build(BuildContext context) {
    final adventureAsync = ref.watch(
      adventureDetailProvider(widget.adventureId),
    );

    return adventureAsync.when(
      data: (adventure) => AdventureDetailsView(adventure: adventure),
      loading: () => Scaffold(
        body: Center(
          child: CircularProgressIndicator(color: Color(0xFF2196F3)),
        ),
      ),
      error: (err, stack) => Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 60, color: Colors.red),
              SizedBox(height: 16),
              Text('Error loading adventure'),
              SizedBox(height: 8),
              TextButton(
                onPressed: () => context.go(AppStrings.homeRoute),
                child: Text('Go Back'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
