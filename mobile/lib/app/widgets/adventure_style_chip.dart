import 'package:flutter/material.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';

class AdventureStyleChip extends StatelessWidget {
  final AdventureStyle style;
  const AdventureStyleChip({super.key, required this.style});

  @override
  Widget build(BuildContext context) {
    final styleData = _getStyleData(style.label);

    return Container(
      padding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: styleData['color'].withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(styleData['icon'], color: styleData['color'], size: 20),
          SizedBox(width: 8),
          Text(
            style.label,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w600,
              color: styleData['color'],
            ),
          ),
        ],
      ),
    );
  }

  Map<String, dynamic> _getStyleData(String style) {
    switch (style) {
      case 'Adventure':
        return {'icon': Icons.nature, 'color': Color(0xFF4CAF50)};
      case 'Relaxing':
        return {'icon': Icons.camera_alt, 'color': Color(0xFFE91E63)};
      case 'Foodie':
        return {'icon': Icons.restaurant, 'color': Color(0xFF2196F3)};
      case 'Culture':
        return {'icon': Icons.account_balance, 'color': Color(0xFF00BCD4)};
      case 'Backpacking':
        return {'icon': Icons.backpack, 'color': Color(0xFFFF9800)};
      case 'Hiking':
        return {'icon': Icons.hiking, 'color': Color(0xFF795548)};
      default:
        return {'icon': Icons.explore, 'color': Colors.grey};
    }
  }
}
