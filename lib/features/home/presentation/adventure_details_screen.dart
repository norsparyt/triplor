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
      data: (adventure) => AdventureDetailsView(
        adventure: adventure,
        onDeletePressed: () => _showDeleteDialog(adventure.id),
      ),
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
              Text(err.toString()),
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

  Future<void> _showDeleteDialog(String adventureId) async {
    return showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext dialogContext) {
        // Use different name to avoid confusion
        return Consumer(
          builder: (context, ref, child) {
            final deleteState = ref.watch(deleteAdventureProvider);
            return AlertDialog(
              title: const Text('Delete Adventure'),
              content: deleteState.isLoading
                  ? Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        CircularProgressIndicator(color: Color(0xFF2196F3)),
                        SizedBox(height: 16),
                        Text('Deleting adventure...'),
                      ],
                    )
                  : const SingleChildScrollView(
                      child: ListBody(
                        children: <Widget>[
                          Text(
                            'Are you sure you want to remove this adventure?',
                          ),
                        ],
                      ),
                    ),
              actions: deleteState.isLoading
                  ? [] // Hide buttons during loading
                  : <Widget>[
                      TextButton(
                        child: const Text('Cancel'),
                        onPressed: () => Navigator.of(dialogContext).pop(),
                      ),
                      TextButton(
                        child: const Text(
                          'Delete',
                          style: TextStyle(color: Colors.red),
                        ),
                        onPressed: () =>
                            _handleDelete(dialogContext, adventureId),
                      ),
                    ],
            );
          },
        );
      },
    );
  }

  Future<void> _handleDelete(
    BuildContext dialogContext,
    String adventureId,
  ) async {
    await ref
        .read(deleteAdventureProvider.notifier)
        .deleteAdventure(adventureId);

    // Check the result after deletion completes
    final deleteState = ref.read(deleteAdventureProvider);

    if (!mounted) return;

    if (deleteState.isDeleted) {
      Navigator.of(dialogContext).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Adventure deleted successfully')),
      );
      // Reset the delete state for future operations
      ref.read(deleteAdventureProvider.notifier).reset();
      // Pop the details screen
      context.go(AppStrings.homeRoute);
    } else if (deleteState.error != null) {
      Navigator.of(dialogContext).pop();
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Error: ${deleteState.error}')));
    }
  }
}
