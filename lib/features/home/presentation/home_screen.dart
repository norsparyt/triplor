import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:triplor/features/home/providers/adventure_providers.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});
  //TODO redo UI
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final adventuresAsync = ref.watch(adventureProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('My Adventures')),
      body: adventuresAsync.when(
        data: (adventure) {
          return ListView.builder(
            itemCount: adventure.length,
            itemBuilder: (context, index) {
              return InkWell(
                borderRadius: BorderRadius.circular(16),
                onTap: () {},
                child: Card(
                  elevation: 3,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  margin: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Location + Type
                        Text(
                          '${adventure[index].location.city}, ${adventure[index].location.country}',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),

                        const SizedBox(height: 8),

                        // Date range
                        Text(
                          _formatDateRange(
                            adventure[index].dateRange.startDate,
                            adventure[index].dateRange.endDate,
                          ),
                          style: Theme.of(
                            context,
                          ).textTheme.bodySmall?.copyWith(color: Colors.grey),
                        ),

                        const SizedBox(height: 12),

                        // Description
                        Text(
                          adventure[index].description,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),

                        const SizedBox(height: 16),
                        // Footer
                        if (adventure[index].maxPeople != null)
                          Text(
                            'Max ${adventure[index].maxPeople} people',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                      ],
                    ),
                  ),
                ),
              );
            },
          );
        },
        error: (error, stack) {
          return Center(child: Text('Error: $error'));
        },
        loading: () => Center(child: CircularProgressIndicator()),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          context.go('/createAdventure');
        },
        child: Icon(Icons.add),
      ),
    );
  }

  String _formatDateRange(DateTime start, DateTime end) {
    return '${start.day}/${start.month} - ${end.day}/${end.month}';
  }
}
