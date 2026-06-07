#!/usr/bin/env node
// 合成 mock 数据 (替代真实抓取)
// 全部用模板 + 种子随机, 无真实信息
const fs = require('fs');
const path = require('path');

const SEED = 20260607;

// 简易 seeded random
let rng = SEED;
function rand() { rng = (rng * 1103515245 + 12345) & 0x7fffffff; return rng / 0x7fffffff; }
function randInt(min, max) { return Math.floor(rand() * (max - min + 1)) + min; }
function pick(arr) { return arr[randInt(0, arr.length - 1)]; }
function pickN(arr, n) { const c = [...arr].sort(() => rand() - 0.5); return c.slice(0, n); }

function randImg(seed) { return `https://picsum.photos/seed/${seed}/640/360`; }
function randAvatar(seed) { return `https://picsum.photos/seed/${seed}/120/120`; }
function randId() { return String(randInt(7000000000000000000, 7999999999999999999)); }

// =============================================================================
// 新闻合成: 7 频道 × 300+ = 2200+ 条
// =============================================================================
const NEWS_CATEGORIES = [
  { id: '推荐', subjects: ['民生', '社会', '文化', '科技', '财经', '体育', '娱乐', '美食', '旅游', '教育', '健康', '时尚'], verbs: ['关注', '热议', '引发讨论', '登上热搜', '成为焦点'], objects: ['的背后', '的故事', '的现象', '的奥秘', '的秘密', '的真相'], count: 350 },
  { id: '社会', subjects: ['城中村', '外卖员', '快递员', '网约车司机', '独居老人', '留守儿童', '志愿者', '社区干部', '基层民警', '环卫工人'], verbs: ['温暖瞬间', '暖心举动', '善举', '助人故事', '凡人微光', '感人事迹'], objects: ['感动全网', '登上热搜', '引千万人点赞', '网友泪目'], count: 300 },
  { id: '财经', subjects: ['A股', '港股', '纳指', '黄金', '原油', '人民币', '比亚迪', '宁德时代', '茅台', '英伟达', '苹果', '特斯拉', '国债'], verbs: ['震荡', '收涨', '收跌', '突破', '创历史新高', '跌回', '反弹', '下挫'], objects: ['机构看好后市', '分析师分歧加大', '北向资金加仓', '散户观望情绪浓', '板块轮动明显'], count: 300 },
  { id: '科技', subjects: ['大模型', 'AI绘画', '自动驾驶', '机器人', '量子计算', '芯片', '卫星', '5.5G', '脑机接口', 'AR眼镜', '折叠屏', '固态电池'], verbs: ['迎来突破', '再升级', '开启新篇章', '落地应用', '进入新阶段', '实现量产'], objects: ['行业格局重塑', '产业链受益', '用户规模破亿', '成本降低90%'], count: 300 },
  { id: '体育', subjects: ['NBA总决赛', '欧冠决赛', '世界杯预选赛', '亚冠', '中超', 'CBA', '法网', '温网', '乒乓球赛', '跳水赛', '田径锦标赛', '羽毛球公开赛', '电竞联赛'], verbs: ['战报', '前瞻', '回顾', '盘点', '总结'], objects: ['球星闪耀', '新星崛起', '老将谢幕', '黑马逆袭', '纪录作古'], count: 300 },
  { id: '视频', subjects: ['美食教程', '旅行vlog', '萌宠日常', '健身跟练', '搞笑短剧', '知识科普', '街头采访', '极限挑战', '手工DIY', '汽车评测'], verbs: ['爆火', '刷屏', '出圈', '走红', '引爆'], objects: ['播放量破亿', '点赞超百万', '网友直呼过瘾', '评论区炸了'], count: 300 },
  { id: '关注', subjects: ['新华社', '人民日报', '央视新闻', '澎湃新闻', '界面新闻', '36氪', '虎嗅', '钛媒体', '极客公园', '三联生活周刊'], verbs: ['最新发布', '深度报道', '权威解读', '独家专访', '现场直击'], objects: ['一文读懂', '全网刷屏', '值得收藏', '深度好文'], count: 100 },
  { id: '国内', subjects: ['一带一路', '乡村振兴', '数字经济', '绿色发展', '科技创新', '民生保障', '教育改革', '医疗改革', '老旧小区改造', '营商环境'], verbs: ['取得新成效', '迈出新步伐', '展现新气象', '开创新局面'], objects: ['百姓点赞', '国际关注', '媒体聚焦', '代表热议'], count: 150 },
  { id: '国际', subjects: ['联合国', 'G20', 'APEC', '欧盟', '东盟', '金砖国家', '上合组织', '世卫组织', '国际奥委会', '国际足联'], verbs: ['召开', '通过', '发布', '达成共识', '签署'], objects: ['影响深远', '备受关注', '引发热议', '多国响应'], count: 200 },
  { id: '娱乐', subjects: ['电影票房', '电视剧收视', '综艺热度', '演唱会', '明星动态', '新歌发布', '综艺节目', '颁奖典礼', '明星恋情', '时尚活动'], verbs: ['创新高', '登顶', '刷屏', '霸榜', '出圈'], objects: ['粉丝狂欢', '网友热议', '话题不断', '持续霸榜'], count: 100 },
];

const newsItems = [];
let newsSeed = 1;
const TITLE_TEMPLATES = [
  (s, v, o) => `${s}${v},${o}`,
  (s, v, o) => `关注｜${s}的${v}新观察`,
  (s, v, o) => `深度｜${s}${v}的背后`,
  (s, v, o) => `热议：${s}为何${v}`,
  (s, v, o) => `${s}：${o}的新注脚`,
  (s, v, o) => `一文看懂${s}的${v}`,
  (s, v, o) => `独家｜${s}${v},引${o}`,
  (s, v, o) => `${o}!${s}${v}引发关注`,
  (s, v, o) => `${s}${v}的${o}，你怎么看？`,
  (s, v, o) => `${s}持续${v},${o}`,
];
for (const cat of NEWS_CATEGORIES) {
  for (let i = 0; i < cat.count; i++) {
    const subj = pick(cat.subjects);
    const verb = pick(cat.verbs);
    const obj = pick(cat.objects);
    const tpl = pick(TITLE_TEMPLATES);
    const title = tpl(subj, verb, obj).slice(0, 38);
    newsItems.push({
      源URL: `https://www.toutiao.com/group/${randId()}/`,
      封面URL: randImg(`news${newsSeed++}`),
      标题: title,
      类别: cat.id,
    });
  }
}
console.log(`News: ${newsItems.length} items`);

// =============================================================================
// 商品合成: 12 品类 × 50+ = 600+ 个
// =============================================================================
const PRODUCT_CATEGORIES = [
  { id: '手机', brands: ['Apple', '华为', '小米', 'OPPO', 'vivo', '荣耀', '三星', '一加', 'realme', 'iQOO'], models: ['Pro Max', 'Pro', 'Plus', 'Ultra', '标准版', '尊享版'], colors: ['钛原色', '远峰蓝', '星河银', '曜石黑', '极光紫', '雪山白', '雾凇金'], storages: ['128GB', '256GB', '512GB', '1TB'], price: [4999, 8999], count: 70 },
  { id: '电脑', brands: ['联想', '华为', '戴尔', '苹果', '惠普', '华硕', '小米', '雷神', '机械革命', '微软'], models: ['游戏本', '轻薄本', '全能本', '二合一', '工作站', '商务本'], specs: ['i5 16G 512G', 'i7 16G 1T', 'i9 32G 2T', 'R7 16G 1T', 'R9 32G 2T'], price: [3999, 14999], count: 60 },
  { id: '数码', brands: ['Apple', '华为', '小米', '大疆', '索尼', '佳能', '富士', '松下', 'Bose', 'JBL'], models: ['旗舰版', '专业版', '标准版', '青春版', '尊享版'], specs: ['256G', '512G', '1T', 'WiFi版', '5G版'], price: [299, 9999], count: 50 },
  { id: '家电', brands: ['美的', '格力', '海尔', '西门子', '松下', '海信', 'TCL', '小米', '卡萨帝', '博世'], models: ['一级能效', '变频', '智能', '云智能', 'AI智控'], specs: ['1匹', '1.5匹', '2匹', '3匹', '大三匹'], price: [1999, 8999], count: 50 },
  { id: '服饰', brands: ['优衣库', 'ZARA', 'H&M', 'GXG', '太平鸟', '李宁', '安踏', '波司登', '江南布衣', '歌莉娅'], models: ['春夏新款', '秋冬上新', '经典款', '联名款', '限量款'], specs: ['S', 'M', 'L', 'XL', 'XXL'], price: [99, 1999], count: 60 },
  { id: '美妆', brands: ['兰蔻', '雅诗兰黛', 'SK-II', '资生堂', '欧莱雅', '玉兰油', '百雀羚', '自然堂', '珀莱雅', '薇诺娜'], models: ['精华液', '面霜', '眼霜', '防晒', '洁面', '面膜'], specs: ['30ml', '50ml', '100ml', '200ml', '礼盒装'], price: [99, 2999], count: 50 },
  { id: '食品', brands: ['三只松鼠', '良品铺子', '百草味', '恰恰', '来伊份', '沃隆', '每日坚果', '好利来', '稻香村', '全聚德'], models: ['混合装', '经典款', '限定款', '家庭装', '尝鲜装'], specs: ['100g', '250g', '500g', '1kg', '礼盒'], price: [29, 399], count: 60 },
  { id: '母婴', brands: ['飞鹤', '君乐宝', '爱他美', '牛栏', '惠氏', '美素佳儿', '好奇', '帮宝适', '大王', '贝亲'], models: ['一段', '二段', '三段', '四段', '孕产妇'], specs: ['400g', '800g', '1.2kg', '2.4kg', '纸尿裤L54'], price: [99, 999], count: 50 },
  { id: '家居', brands: ['宜家', '全友', '林氏木业', '顾家家居', '芝华仕', '慕思', '喜临门', '源氏木语', '造作', '吱音'], models: ['北欧风', '现代简约', '新中式', '美式', '轻奢'], specs: ['1.2m', '1.5m', '1.8m', '2.0m', '定制'], price: [299, 9999], count: 50 },
  { id: '运动', brands: ['耐克', '阿迪达斯', '李宁', '安踏', '特步', '361°', '鸿星尔克', '亚瑟士', '美津浓', '彪马'], models: ['跑步鞋', '篮球鞋', '训练鞋', '休闲鞋', '复古款'], specs: ['39', '40', '41', '42', '43', '44'], price: [199, 1999], count: 50 },
  { id: '汽车', brands: ['比亚迪', '特斯拉', '小鹏', '理想', '蔚来', '问界', '小米', '极氪', '长安', '吉利'], models: ['纯电版', '增程版', '混动版', '旗舰版', '标准续航'], specs: ['500km', '600km', '700km', '800km', '1000km'], price: [99999, 499999], count: 50 },
  { id: '图书', brands: ['人民文学出版社', '商务印书馆', '中华书局', '中信出版社', '三联书店', '磨铁', '新经典', '博集天卷', '后浪', '读客'], models: ['精装', '平装', '典藏版', '纪念版', '套装'], specs: ['全本', '精选本', '上下册', '全三册', '全五册'], price: [29, 599], count: 50 },
];

const productItems = [];
let prodSeed = 1;
for (const cat of PRODUCT_CATEGORIES) {
  if (!cat.brands || !cat.models) {
    console.log(`BAD CAT: ${cat.id}`, Object.keys(cat));
    continue;
  }
  const specPool = cat.specs || cat.storages || cat.colors || ['标准款'];
  for (let i = 0; i < cat.count; i++) {
    const brand = pick(cat.brands);
    const model = pick(cat.models);
    const spec = pick(specPool);
    const color = cat.colors ? pick(cat.colors) : '';
    const nameParts = [
      `${brand}`,
      cat.id === '图书' ? model : spec,
      cat.id === '图书' ? `${cat.id} ${model}` : `${cat.id} ${model}`,
    ].filter(Boolean);
    const name = nameParts.join(' ').slice(0, 50);
    const price = randInt(cat.price[0], cat.price[1]);
    const origPrice = price + randInt(100, 1000);
    const sold = randInt(100, 50000);
    const productId = String(randInt(10000000000, 99999999999));
    productItems.push({
      商品ID: productId,
      名称: name.slice(0, 60),
      类别: cat.id,
      源URL: `https://product.example.com/${cat.id}/${productId}.html`,
      封面URL: randImg(`prod${prodSeed++}`),
      价格: price,
      原价: origPrice,
      销量: sold,
    });
  }
}
console.log(`Products: ${productItems.length} items`);

// =============================================================================
// 写文件
// =============================================================================
const OUT_DIR = '/tmp/toutiao_synthesize';
fs.mkdirSync(OUT_DIR, { recursive: true });

fs.writeFileSync(
  path.join(OUT_DIR, 'news_data.json'),
  JSON.stringify({ 新闻: newsItems }, null, 2)
);

fs.writeFileSync(
  path.join(OUT_DIR, 'mall_products.json'),
  JSON.stringify({ 商品: productItems }, null, 2)
);

console.log(`Saved to ${OUT_DIR}`);
